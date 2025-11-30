import http from 'k6/http';
import { sleep, check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

// Custom metric to track write→read freshness delay
export const freshness_delay_ms = new Trend('freshness_delay_ms');
export const freshness_timeout = new Rate('freshness_timeout');

export const options = {
    vus: 20,
    duration: '30s',
    thresholds: {
        // Freshness SLA: average ≤ 1000ms, worst-case ≤ 3000ms
        freshness_delay_ms: ['avg<=1000', 'max<=3000'],
        // No timeouts allowed in steady state
        'freshness_timeout': ['rate==0'],
    },
};

export default function () {
    // Spread writes across products to avoid heavy contention between VUs
    const productId = ((__VU * 7919) % 100000) + 1; // assumes DB seeded with ≥100k products

    const urls = getUrls();
    const writeIdx = urls.length > 0 ? (Number(__VU) + Number(__ITER)) % urls.length : 0;
    const writeBaseUrl = urls[writeIdx] || 'http://localhost:8080';

    // Read current price BEFORE write to have a stable comparison target
    const before = http.get(`${writeBaseUrl}/price/${productId}`);
    check(before, { 'pre-read ok': r => r.status === 200 });
    const beforeBody = safeJson(before.body);
    const prevFinal = beforeBody?.finalPrice;

    // 1) Perform a write that likely changes the final price
    const newValue = Math.floor(Math.random() * 1000) + 1; // widen range to reduce chance of equality
    const payload = JSON.stringify([
        { type: 'PROMO', value: newValue, mode: 'ABSOLUTE' }
    ]);

    const update = http.post(
        `${writeBaseUrl}/admin/price/${productId}/adjustments`,
        payload,
        { headers: { 'Content-Type': 'application/json' } }
    );

    // ensure write succeeded
    check(update, { 'write ok': r => r.status === 200 });

    const start = Date.now();
    let updatedSeen = false;
    let delay = 5000; // default to timeout value

    // 2) Poll GET /price until updated price appears or timeout (5s)
    while (!updatedSeen && Date.now() - start < 5000) {
        // poll all known URLs to validate cross-replica propagation
        for (let i = 0; i < urls.length && !updatedSeen; i++) {
            const readUrl = urls[i];
            const res = http.get(`${readUrl}/price/${productId}`);
            if (res.status !== 200) {
                continue;
            }
            const body = safeJson(res.body);
            const finalPrice = body?.finalPrice;

            if (finalPrice != null && prevFinal != null && finalPrice !== prevFinal) {
                updatedSeen = true;
                delay = Date.now() - start;
                freshness_delay_ms.add(delay);
                // mark this iteration as non-timeout for the rate metric denominator
                freshness_timeout.add(false);

                check({ delay }, {
                    'freshness <= 3000ms': (d) => d.delay <= 3000,
                });
                break;
            }
        }
        if (!updatedSeen) sleep(0.05);
    }

    if (!updatedSeen) {
        // Record timeout case as 5000ms to reflect worst-case
        freshness_delay_ms.add(5000);
        freshness_timeout.add(1);
        check(null, { 'freshness updated within 5s': () => false });
    }
}

function safeJson(body) {
    try { return JSON.parse(body); } catch (_) { return null; }
}

export function handleSummary(data) {
    const m = data.metrics?.['freshness_delay_ms'];
    const t = data.metrics?.['freshness_timeout'];
    const checks = data.metrics?.checks;
    const runs = ((checks?.values?.passes || 0) + (checks?.values?.fails || 0));

    const summary = {
        runs,
        avgFreshnessMs: m?.values?.avg,
        p95FreshnessMs: m?.values?.['p(95)'],
        maxFreshnessMs: m?.values?.max,
        timeoutRate: t?.values?.rate,
        thresholds: data.thresholds,
    };
    return {
        stdout: `\nFreshness SLA summary:\n${JSON.stringify(summary, null, 2)}\n`,
    };
}

function getUrls() {
    // Support single BASE_URL or comma-separated BASE_URLS to exercise multiple replicas
    if (__ENV.BASE_URLS) {
        const urls = __ENV.BASE_URLS.split(',').map(s => s.trim()).filter(Boolean);
        if (urls.length > 0) {
            return urls;
        }
    }
    const single = __ENV.BASE_URL || 'http://localhost:8080';
    return [single];
}
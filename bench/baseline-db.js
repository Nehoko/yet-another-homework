import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
    vus: 50,
    duration: '30s',
};

export default function () {
    const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
    const productId = Math.floor(Math.random() * 100_000) + 1;
    http.get(`${baseUrl}/price-db/${productId}`);
    sleep(0.001);
}

export function handleSummary(data) {
    const dur = data.metrics?.['http_req_duration']?.values || {};
    const count = data.metrics?.['http_reqs']?.values?.count;
    const summary = {
        requests: count,
        avgMs: dur.avg,
        p50Ms: dur['p(50)'],
        p90Ms: dur['p(90)'],
        p95Ms: dur['p(95)'],
        p99Ms: dur['p(99)'],
        maxMs: dur.max,
    };
    return {
        stdout: `\nDB-only baseline summary:\n${JSON.stringify(summary, null, 2)}\n`,
    };
}
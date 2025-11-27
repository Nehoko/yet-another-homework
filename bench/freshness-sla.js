import http from 'k6/http';
import { sleep, check } from 'k6';

export const options = {
    vus: 20,
    duration: '15s'
};

export default function () {
    const productId = 9999;

    // 1) Perform a write
    const payload = JSON.stringify({
        type: "PROMO",
        value: Math.floor(Math.random() * 10) + 1,
        mode: "ABSOLUTE"
    });

    const update = http.post(
        `http://localhost:8080/admin/price/${productId}/adjustments`,
        payload,
        { headers: { "Content-Type": "application/json" } }
    );

    // ensure write succeeded
    check(update, { "write ok": r => r.status === 200 });

    const start = Date.now();
    let updatedSeen = false;

    // 2) Poll GET /price until updated price appears
    while (!updatedSeen && Date.now() - start < 5000) {
        const res = http.get(`http://localhost:8080/price/${productId}`);

        const body = JSON.parse(res.body);
        const finalPrice = body.finalPrice;

        if (finalPrice !== body.oldValue) {
            updatedSeen = true;
            const delay = Date.now() - start;

            console.log(`Freshness delay: ${delay}ms`);

            check(null, {
                "freshness <= 3000ms": () => delay <= 3000,
                "freshness <= 1000ms average?": () => delay <= 1000
            });
        }

        sleep(0.05);
    }
}
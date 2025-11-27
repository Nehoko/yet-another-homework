import http from 'k6/http';

export const options = {
    vus: 100,
    duration: '45s',

    thresholds: {
        http_req_duration: ["p(95)<10", "p(99)<20"], // assignment metrics
    },
};

export default function () {
    // use a hot productId to hit cache
    const productId = 12345;
    http.get(`http://localhost:8080/price/${productId}`);
}
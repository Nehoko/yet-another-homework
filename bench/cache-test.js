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
    const productId = Number(__ENV.HOT_ID) || 12345;
    const baseUrl = __ENV.BASE_URL || 'http://localhost:8080';
    http.get(`${baseUrl}/price/${productId}`);
}
import http from 'k6/http';

export const options = {
    scenarios: {
        high_rps: {
            executor: "constant-arrival-rate",
            rate: 5000,        // 5k requests per second
            timeUnit: "1s",
            duration: "60s",
            preAllocatedVUs: 500,
            maxVUs: 1000,
        }
    }
};

export default function () {
    const id = Math.floor(Math.random() * 100000);
    http.get(`http://localhost:8080/price/${id}`);
}
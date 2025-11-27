import http from 'k6/http';
import { sleep } from 'k6';

export const options = {
    vus: 50,
    duration: '30s',
};

export default function () {
    const productId = Math.floor(Math.random() * 100_000);
    http.get(`http://localhost:8080/products/${productId}`);
    sleep(0.001);
}
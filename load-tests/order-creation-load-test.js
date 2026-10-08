import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
    vus: 50,
    duration: '30s',
    thresholds: {
        http_req_duration: ['p(95)<2000', 'p(99)<5000'],
        http_req_failed: ['rate<0.05'],
    },
};

const BASE_URL = 'http://localhost:8080';
const PRODUCT_ID = '22222222-2222-2222-2222-222222222222';

export default function () {
    const customerId = uuidv4();
    const idempotencyKey = uuidv4();

    const payload = JSON.stringify({
        customerId: customerId,
        productId: PRODUCT_ID,
        quantity: 1,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotencyKey,
        },
    };

    const res = http.post(`${BASE_URL}/api/orders`, payload, params);

    check(res, {
        'status is 201': (r) => r.status === 201,
    });

    sleep(0.1);
}
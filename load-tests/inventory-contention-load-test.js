import http from 'k6/http';
import { check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';
import { Counter } from 'k6/metrics';

const successCounter = new Counter('successful_reservations');
const failureCounter = new Counter('failed_reservations');

export const options = {
    vus: 50,
    iterations: 50,
};

const BASE_URL = 'http://localhost:8080';
const LOW_STOCK_PRODUCT_ID = '44444444-4444-4444-4444-444444444444'; // stock = 1

export default function () {
    const payload = JSON.stringify({
        customerId: uuidv4(),
        productId: LOW_STOCK_PRODUCT_ID,
        quantity: 1,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': uuidv4(),
        },
    };

    const res = http.post(`${BASE_URL}/api/orders`, payload, params);
    const body = JSON.parse(res.body);

    check(res, { 'status is 201': (r) => r.status === 201 });

    if (body.status === 'INVENTORY_RESERVED') {
        successCounter.add(1);
    } else if (body.status === 'CANCELLED') {
        failureCounter.add(1);
    }
}
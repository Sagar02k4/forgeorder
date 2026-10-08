# API Reference

All mutating endpoints require an `Idempotency-Key` header. An optional `X-Correlation-ID` header is accepted; if omitted, one is generated and returned in error responses.

Full interactive documentation with request/response schemas: `http://localhost:8080/swagger-ui/index.html`

## Error shape

Every error response follows the same shape:

```json
{
  "code": "INVALID_STATE_TRANSITION",
  "message": "Cannot transition order from CANCELLED to PAYMENT_PENDING",
  "correlationId": "8b252c11-f5b0-4095-be39-808e82231737",
  "timestamp": "2026-10-02T11:22:57.69Z"
}
```

| Code | HTTP Status | Meaning |
|---|---|---|
| `VALIDATION_FAILED` | 400 | Request body failed field validation |
| `INVALID_WEBHOOK_SIGNATURE` | 401 | HMAC signature on a webhook didn't match |
| `IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD` | 409 | Same key, different request body |
| `REQUEST_IN_PROGRESS` | 409 | A request with this key is still being processed |
| `INVALID_STATE_TRANSITION` | 409 | Order isn't in a state that allows this action |
| `INVALID_REFUND` | 409 | Refund exceeds captured amount, or order isn't refundable |
| `ORDER_NOT_FOUND` / `PRODUCT_NOT_FOUND` | 404 | — |
| `RATE_LIMIT_EXCEEDED` | 429 | More than 20 order-creation requests from the same `customerId` within 60 seconds |
| `INTERNAL_ERROR` | 500 | Unhandled exception (catch-all) |

## Orders

### `POST /api/orders`

Creates an order. Price and tax are computed server-side from the catalog — the client supplies `productId` and `quantity` only, never an amount.

**Request**
```json
{
  "customerId": "11111111-1111-1111-1111-111111111111",
  "productId": "22222222-2222-2222-2222-222222222222",
  "quantity": 1
}
```

**Response** `201 Created`
```json
{
  "id": "...",
  "customerId": "...",
  "productId": "...",
  "quantity": 1,
  "status": "INVENTORY_RESERVED",
  "subtotal": 100.0000,
  "tax": 18.0000,
  "totalAmount": 118.0000,
  "createdAt": "...",
  "updatedAt": "...",
  "inventory": { "availableQuantity": 99, "reservedQuantity": 1 },
  "payment": null
}
```

If stock isn't available, the order is still created (so the customer has a record of what happened) but ends in `CANCELLED` rather than `INVENTORY_RESERVED` — this is an outcome, not an error.

### `GET /api/orders/{id}`

Returns the order enriched with current inventory and the most recent payment attempt, if any. `inventory` and `payment` are `null` where not yet applicable.

### `POST /api/orders/{id}/payments`

Initiates payment against a reserved order using a mock card token (see [usage.md](usage.md) for the available tokens and what each simulates).

**Request**
```json
{ "cardToken": "tok_success_visa" }
```

**Response** `200 OK`
```json
{ "orderId": "...", "orderStatus": "CONFIRMED", "message": "Payment processed with outcome: CONFIRMED" }
```

### `POST /api/orders/{id}/cancel`

No request body. Behavior depends on the order's current state:
- `CREATED` / `INVENTORY_RESERVED` → inventory is released, order becomes `CANCELLED`
- `CONFIRMED` / `FULFILLED` → a full refund is initiated instead (cancelling a paid order means refunding it, not pretending the cancellation alone settles it)
- Any other state → `409 INVALID_STATE_TRANSITION`

### `POST /api/orders/{id}/refunds`

**Request**
```json
{ "amount": 118.00 }
```

Rejected with `409 INVALID_REFUND` if the order isn't `CONFIRMED`/`FULFILLED`, or if this amount plus the sum of previously *succeeded* refunds on this order would exceed what was actually captured.

## Webhooks

### `POST /api/webhooks/mock-payment`

Receives an asynchronous payment status update from the (mock) gateway. Requires header `X-Webhook-Signature`, an HMAC-SHA256 signature over the raw request body.

- Signature invalid → `401 INVALID_WEBHOOK_SIGNATURE`
- `providerEventId` already seen → acknowledged with `200 OK`, no effect reapplied
- Order isn't in a state where this event is a valid transition (already confirmed, already cancelled) → silently ignored, still `200 OK`

### `POST /api/test/webhooks/simulate`

Test-only helper. Given a `providerPaymentId` and `eventType`, returns a correctly-signed payload that can be replayed against `/api/webhooks/mock-payment` — including replaying it multiple times to demonstrate duplicate handling.

### `GET /api/test/webhooks/gateway-status?idempotencyKey=...`

Test-only helper. Surfaces what the mock gateway's internal state has for a given idempotency key — needed because after a simulated timeout, the application genuinely doesn't have a `providerPaymentId` to work with, only the gateway does.

## Admin / Operations

### `GET /api/admin/orders?status=RECONCILIATION_REQUIRED`

Lists orders by status. Returns a summary shape (no per-order inventory/payment lookups, to avoid an N+1 query on a list endpoint).

### `POST /api/admin/orders/{id}/reconcile`

Manually triggers reconciliation for one order immediately, rather than waiting for the scheduled worker's next cycle.

## Not yet implemented

`/actuator/prometheus` is not exposed (dependency present, not wired up — see [decisions.md](decisions.md)). There is no authentication layer, so admin endpoints are currently reachable by anyone; see the same section for why.

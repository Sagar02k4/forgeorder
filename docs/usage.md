# Usage Guide

Assumes the app is already running — see [installation.md](installation.md) if not.

## Seed data

Three products are seeded on first startup (Flyway `V6`/`V8`):

| Product ID | Name | Stock | Price | Tax rate |
|---|---|---|---|---|
| `22222222-2222-2222-2222-222222222222` | Test Product A | 100 | 100.00 | 18% |
| `33333333-3333-3333-3333-333333333333` | Test Product B | 50 | 50.00 | 18% |
| `44444444-4444-4444-4444-444444444444` | Test Product C (Low Stock) | 1 | 25.00 | 18% |

Product C has exactly 1 unit on purpose — useful for triggering an `INVENTORY_UNAVAILABLE` → `CANCELLED` order without needing concurrent requests.

If stock on these runs out from testing, reset with `docker compose down -v && docker compose up -d`.

## Mock payment tokens

Payment outcomes are driven entirely by the `cardToken` prefix sent to `POST /api/orders/{id}/payments` — there's no real gateway behind this.

| Token prefix | Outcome |
|---|---|
| `tok_success_*` | Immediate success → order `CONFIRMED` |
| `tok_decline_*` | Immediate decline → order `PAYMENT_FAILED` |
| `tok_timeout_*` | Simulated timeout → order `RECONCILIATION_REQUIRED` (gateway records success internally, application doesn't know yet) |
| anything else | `400`, unrecognized token |

## Walkthrough: duplicate request protection

1. `POST /api/orders` with `Idempotency-Key: demo-key-1` and a body.
2. Repeat the exact same request with the same key and body → identical response, identical order ID, no second order created.
3. Repeat again with the same key but a different body (e.g. different `quantity`) → `409 IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD`.

## Walkthrough: last-unit concurrency

This is proven automatically by `InventoryServiceConcurrencyTest` (100 simulated concurrent buyers, 1 unit of stock, exactly one succeeds) — see [testing.md](testing.md). To see it manually: order against Product C (`444...`, stock = 1) twice in a row; the second order will come back `CANCELLED` due to `INVENTORY_UNAVAILABLE`.

## Walkthrough: payment timeout → reconciliation → confirmed

1. Create an order, note its ID.
2. `POST /api/orders/{id}/payments` with `cardToken: tok_timeout_visa`. Order becomes `RECONCILIATION_REQUIRED`.
3. Either wait up to 30 seconds for the scheduled reconciliation worker, or trigger it immediately:
   `POST /api/admin/orders/{id}/reconcile`
4. `GET /api/orders/{id}` → status is now `CONFIRMED`, and the payment's `providerPaymentId` is populated — the reconciliation worker queried the gateway by idempotency key and discovered the charge had actually succeeded.

## Walkthrough: webhook signature + duplicate delivery

1. Create an order and pay with `tok_timeout_visa` as above (don't let it reconcile yet).
2. Find the idempotency key you used for that payment, then:
   `GET /api/test/webhooks/gateway-status?idempotencyKey=<key>` → returns the gateway's internal `providerPaymentId`.
3. `POST /api/test/webhooks/simulate` with that `providerPaymentId` and `eventType: PAYMENT_SUCCEEDED` → returns a signed `payload` + `signature`.
4. `POST /api/webhooks/mock-payment` with header `X-Webhook-Signature: <signature>` and that exact `payload` string as the raw body → `200 OK`, order becomes `CONFIRMED`.
5. Replay the exact same request again (same payload, same signature) → still `200 OK`, but check `payment_webhook_events` in the database — still exactly one row. The second delivery was acknowledged without reapplying anything.

## Walkthrough: fulfillment and notification run independently

1. Create and confirm an order (any successful-payment path).
2. Within ~10–15 seconds (the outbox publisher's cycle), check the application logs for two independent lines:
   `[NOTIFICATION] Order ... -> ORDER_CONFIRMED ...`
   `[FULFILLMENT] Order ... shipped. Reference: ship_...`
3. Both consumed the same `ORDER_CONFIRMED` event from the same fanout exchange, independently — stopping one doesn't block the other.

## Walkthrough: rate limiting

Fire more than 20 `POST /api/orders` requests within 60 seconds using the same `customerId` (with distinct `Idempotency-Key` values, otherwise idempotency short-circuits before the limit is even reached) — requests 21 onward return `429 RATE_LIMIT_EXCEEDED`. Check the counter directly:

```bash
docker exec -it forgeorder-redis redis-cli GET "rate-limit:order-creation:<customerId>"
```

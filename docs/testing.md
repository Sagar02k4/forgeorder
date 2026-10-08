# Testing

```bash
mvn test
```

Docker must be running — integration and concurrency tests use Testcontainers to spin up a real, disposable PostgreSQL instance per test class rather than mocking the database. Nothing here is proven against an in-memory or mocked datastore; the claims this project makes (no oversold stock, no duplicate orders, correct state transitions) are only credible if they're checked against the same database engine running in production.

## Unit tests

**`OrderStateMachineTest`** — valid and invalid transitions, including the specific bug this state machine exists to prevent (`CONFIRMED → PAYMENT_PENDING`), plus a parameterized check that no state can transition to itself, and that both terminal states (`CANCELLED`, `REFUNDED`) permit no outgoing transition at all.

**`OrderTest`** — a new `Order` always starts `DRAFT`; a valid `transitionTo()` call updates status and returns a matching `OrderAuditEvent`; an invalid one throws and leaves the order's status unchanged (verified explicitly, not just that an exception was thrown).

**`MockPaymentGatewayTest`** — each token prefix produces the expected outcome; an unrecognized token throws rather than defaulting to any particular outcome; `queryStatus()` after a simulated timeout reveals the outcome the gateway actually recorded; querying a key that was never charged returns `NOT_FOUND` rather than an error.

## Integration tests

**`OrderServiceIntegrationTest`** — order creation persists the order and its audit trail in the same transaction; when stock is unavailable the order still exists (not rolled back) but ends `CANCELLED` with the full audit chain recorded, rather than the request simply failing.

**`PaymentServiceIntegrationTest`** — all three gateway outcomes end the order in the correct state (`CONFIRMED` / `PAYMENT_FAILED` / `RECONCILIATION_REQUIRED`), and specifically that the `UNKNOWN`-status payment attempt has no `providerPaymentId` recorded, since that's the premise the reconciliation mechanism depends on.

**`ReservationExpiryIntegrationTest`** — a reservation forced into the past is released by the scheduler: stock returns to its prior available count, reserved count returns to zero, reservation status becomes `EXPIRED`.

## Concurrency tests

These are the two tests this project is actually about. Both use the same pattern: a thread pool is launched, every thread blocks on a shared `CountDownLatch` immediately after starting, and only once *all* threads report ready does the main thread release the latch — so the race is real rather than threads trickling in one after another and effectively queuing.

**`IdempotencyServiceConcurrencyTest`** — 20 threads call `beginOperation` with the identical idempotency key at the same instant. Exactly one reports `PROCEED`; the other 19 report `IN_PROGRESS`.

**`InventoryServiceConcurrencyTest`** — 100 threads attempt to reserve 1 unit each against a product with exactly 1 unit of stock, simultaneously. Exactly one succeeds; the other 99 are told no stock was available; final inventory state is `available: 0, reserved: 1` — never negative, never double-counted.

## End-to-end tests

**`ReconciliationServiceIntegrationTest`** — the full cycle: create an order, pay with a timeout-simulating token (order lands in `RECONCILIATION_REQUIRED`), run reconciliation, assert the order is now `CONFIRMED` and the payment attempt shows `SUCCEEDED` with a `providerPaymentId` populated — none of which existed before reconciliation ran.

## What isn't covered by automated tests

The webhook signature/duplicate-delivery flow and the rate-limiter were verified manually via Swagger/curl during development rather than captured as automated `MockMvc`/`WebTestClient` tests — see the webhook and rate-limiting walkthroughs in [usage.md](usage.md) to reproduce them. Load testing (p95/p99 latency under realistic concurrent HTTP traffic, as opposed to in-process thread concurrency) is also not part of this suite — see [decisions.md](decisions.md#scope-boundaries).

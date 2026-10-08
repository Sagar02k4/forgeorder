# Benchmarks

Two different kinds of measurement live in this project, and they answer different questions. Both are reported here rather than blended into one number, because conflating them would overstate what either one actually proves.

## 1. Correctness under concurrency (JVM-level)

These come from the JUnit concurrency tests (`mvn test`), which fire real parallel threads at the same service method simultaneously — not sequential calls, synchronized with a `CountDownLatch` so every thread starts at the same instant.

| Test | Concurrent actors | Contested resource | Result |
|---|---|---|---|
| `IdempotencyServiceConcurrencyTest` | 20 threads | Same idempotency key | 1 proceeded, 19 correctly told "in progress" |
| `InventoryServiceConcurrencyTest` | 100 threads | 1 unit of stock | 1 reservation succeeded, 99 correctly rejected, stock never negative |

What this proves: the atomic database operations behind idempotency and inventory reservation hold under genuine concurrent contention, not just sequential calls that happen to look concurrent.

## 2. HTTP-level load test (k6)

Run against the live application (`docker compose up -d`, app running locally) — real network requests, not in-process method calls.

### Order creation under sustained load

`load-tests/order-creation-load-test.js` — 50 virtual users, continuous requests for 30 seconds, each creating an order against a product with ample stock (`productId: 22222222-...`, 100 units) using a fresh `customerId` and `Idempotency-Key` per request.

| Metric | Result |
|---|---|
| Total requests | 6,444 |
| Request rate | ~213 req/s |
| Failure rate | 0.00% |
| p90 latency | 174.02 ms |
| p95 latency | 192.78 ms |
| p99 latency | 252.91 ms |
| Max latency | 396.28 ms |

Every request created a fully-reserved order — meaning each one went through order creation, catalog price lookup, and an atomic inventory update, all inside one transaction, within these latencies.

### Inventory contention under load

`load-tests/inventory-contention-load-test.js` — 50 virtual users firing one request each, simultaneously, against a product seeded with exactly 1 unit of stock (`productId: 44444444-...`).

| Metric | Result |
|---|---|
| Total requests | 50 |
| Successful reservations | 1 |
| Correctly rejected (stock unavailable) | 49 |
| p95 latency | 248.41 ms |

This is the same property `InventoryServiceConcurrencyTest` proves at the JVM-thread level, reproduced here over real HTTP — the atomic conditional update resolves the race at the database layer regardless of whether the concurrent callers are threads in one process or genuinely separate HTTP clients.

## Reproducing these numbers

```bash
docker compose down -v && docker compose up -d   # fresh seed data
mvn spring-boot:run                               # separate terminal
k6 run load-tests/order-creation-load-test.js
k6 run load-tests/inventory-contention-load-test.js
```

The contention test depends on the seeded low-stock product having its full 1 unit available — if it's been exhausted by earlier manual testing, reset the database first (see [installation.md](installation.md#resetting-to-a-clean-state)).

## What these numbers are not

These were run on a single developer machine against a single application instance, not a production-equivalent deployment, and don't include sustained load over minutes/hours, memory or CPU profiling under load, or behavior with RabbitMQ/Postgres under simultaneous external load rather than idle. They demonstrate the correctness and rough latency characteristics of the core write paths, not a capacity-planning figure for any specific production deployment.

# Technical Decisions

## Modular monolith over microservices

Everything runs as one Spring Boot process, internally partitioned by package with a strict rule that one module never touches another's persistence layer. Microservices would have added network calls, partial-failure modes, and distributed-transaction problems *on top of* the concurrency and reliability problems this project already exists to solve — without making any of those problems more interesting to solve. The module boundaries are real (enforced by not importing across them, not by physical separation), which is what makes a later split possible if it were ever actually needed.

## Database-enforced concurrency instead of application-level locks

The first version of inventory reservation considered was read-then-decrement in application code. That's a textbook race condition: two threads both read `available_quantity = 1`, both decide they're allowed to buy, both decrement, and the result is `-1` with two buyers both told they succeeded. It was replaced with a single atomic `UPDATE ... SET available_quantity = available_quantity - :qty WHERE available_quantity >= :qty`, where the database itself resolves the race — the losing request simply updates zero rows and is told "no stock," rather than ever reading a stale value. The same pattern — try the write, inspect how many rows it affected rather than checking first and writing second — reappears for idempotency key insertion, webhook event deduplication, and consumer message deduplication (`processed_messages`). It's the one concurrency idea this project leans on repeatedly rather than reinventing.

Proven with a test that fires 100 threads at one unit of stock simultaneously (synchronized with a `CountDownLatch` so they actually race rather than queue up sequentially) and asserts exactly one succeeds and the final stock is never negative.

## Idempotency as insert-and-catch, not check-then-insert

`IdempotencyService.beginOperation` doesn't check whether a key exists and then insert if not — that has the same race-condition shape as the inventory problem, just with an HTTP request racing another HTTP request instead of two buyers. It attempts the insert directly and catches the resulting `DataIntegrityViolationException` if another request won the race, at which point it re-reads and returns that request's outcome instead. Proven with 20 threads submitting the same idempotency key simultaneously: exactly one proceeds, the rest correctly report "in progress."

## Payment: three outcomes, not two

A gateway call can succeed, fail, or — critically — time out without telling you which. Treating a timeout as a failure is wrong: if the charge actually succeeded on the provider's side and the order gets cancelled locally, the customer has paid for nothing and may be prompted to pay again. `PaymentGatewayResult` and the order state machine both have a third branch (`PAYMENT_STATUS_UNKNOWN` → `RECONCILIATION_REQUIRED`) specifically so this case is never silently collapsed into either success or failure.

Two independent mechanisms resolve that uncertainty, deliberately both present rather than just one:
- **Reconciliation** (pull) — a scheduled worker re-queries the gateway by idempotency key.
- **Webhooks** (push) — the gateway notifies the application asynchronously, verified by HMAC signature and deduplicated by provider event ID.

Real systems run both, because either channel alone has a failure mode the other covers (a webhook can be lost; a reconciliation poll has to wait for its next cycle). The mock gateway models this honestly: when `charge()` is called with a token that simulates a timeout, the gateway's internal state *does* record success — the application genuinely doesn't know that yet, which is the entire point of the scenario.

## REQUIRES_NEW transaction boundaries

Several coordinator methods (`PaymentService.initiatePayment`, `RefundService.initiateRefund`, `ReservationExpiryScheduler`'s interaction with `InventoryService`) are deliberately *not* `@Transactional` themselves, delegating to inner methods marked `Propagation.REQUIRES_NEW`. This was not the original design — the first version of idempotent insert handling caught a `DataIntegrityViolationException` inside the same transaction as the outer method, which compiled and looked correct, and then failed at commit time with `UnexpectedRollbackException`. The cause: once an exception crosses a `@Transactional` boundary, Spring marks that transaction rollback-only regardless of whether the exception is subsequently caught. `REQUIRES_NEW` gives the step that's expected to possibly fail its own transaction, so a handled failure there doesn't silently poison a transaction the caller believes is still healthy.

## Why money fields are `BigDecimal`, never `double`

`double` arithmetic has representable-value errors (`0.1 + 0.2 != 0.3`) that are unacceptable for anything involving currency. Every monetary field is `BigDecimal` with explicit `precision`/`scale`, both in Java and in the matching Postgres `NUMERIC(19,4)` columns.

## Why entity status fields have no public setter

`Order`, `IdempotencyRecord`, `PaymentAttempt`, `Refund`, and `OutboxEvent` all expose status changes only through named methods (`transitionTo`, `markSucceeded`, `markFailed`, ...), never a bare setter. The alternative — a public `setStatus()` anyone can call — makes the state machine advisory rather than enforced; a single careless call anywhere in the codebase could produce an impossible state. Pushing the check into the entity itself means it's enforced everywhere the entity is used, not just at the one call site someone remembered to validate.

## Why IDs are generated in application code, not by the database

Every entity's primary key is assigned with `UUID.randomUUID()` in the Java constructor rather than left to a database default. This matters specifically for the outbox pattern: an order's ID needs to exist *before* the row is inserted, so that the `OutboxEvent` referencing it can be constructed and saved in the same transaction. Database-generated IDs would mean a round-trip to get the ID back before the related rows could be built, which complicates exactly the atomicity this pattern depends on.

---

## Scope boundaries

Deliberately out of scope, and why:

- **Authentication / authorization** — there is no user/role model, so every endpoint (including `/api/admin/*`) is currently open. Document section 7.1 calls for `CUSTOMER` / `SUPPORT_AGENT` / `OPERATIONS_ADMIN` / `SYSTEM_WORKER` roles with ownership checks; building that properly (JWT issuance, refresh, per-resource ownership) is a project-sized piece of work on its own, and was left out to keep focus on the correctness/concurrency story this project is actually about.
- **Multiple items per order** — an order currently holds one `productId` + `quantity` directly rather than a collection of `OrderItem` rows. Extending this is mostly mechanical (an `OrderItem` entity, a loop over inventory reservation per line) but touches request/response shapes across every module and was deferred rather than rushed.
- **Partial refunds** — a successful refund currently moves the order straight to the terminal `REFUNDED` state, which forecloses a second partial refund on the same order even though the amount-validation logic already supports it correctly. Production behavior would keep the order in `CONFIRMED`/`FULFILLED` until cumulative refunds equal the captured amount.
- **Inventory restocking on a paid-order refund** — cancelling an unpaid order releases its reservation back to available stock; refunding a paid, already-committed order currently does not restock it, since returns/restocking is a distinct operational process (quality check, warehouse handling) that the document doesn't specify a behavior for.
- **Observability** — Actuator's dependency is present but only the default `/actuator/health` endpoint is exposed; structured logging, metrics, and tracing (section 7.15) weren't built out.
- **Load testing** — concurrency correctness is proven at the JVM-thread level (JUnit tests with real parallel threads against a real database via Testcontainers), not at the HTTP/network level with a tool like k6 or Gatling measuring p95/p99 latency under load.
- **Catalog management** — products are seeded via a Flyway migration; there's no admin endpoint to add one, so the system ships with exactly three fixed test products.

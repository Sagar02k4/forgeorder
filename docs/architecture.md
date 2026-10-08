# Architecture

## Shape of the system

ForgeOrder is a **modular monolith**: one Spring Boot process, but internally partitioned into packages that behave like independent modules. A module exposes an `application` service as its public interface; nothing outside the module touches its `persistence` repositories or `domain` entities directly. This was a deliberate choice over microservices — the project's actual subject is correctness under concurrency and failure, not distributed deployment, and splitting into services would have added network boundaries and partial-failure modes without adding anything to that story.

```
Client / Swagger UI
        │
        ▼
┌─────────────────────────────────────────────┐
│  REST Controllers (orders, admin, webhooks)  │
│  — idempotency check, validation —           │
└───────────────────┬───────────────────────────┘
                     ▼
   ┌───────────────────────────────────────┐
   │   Application services (per module)    │
   │   orders · inventory · payments ·       │
   │   refunds · reconciliation · webhooks   │
   └───────┬───────────────────┬─────────────┘
           ▼                   ▼
     PostgreSQL           RabbitMQ (fanout)
   (source of truth)        │        │
           ▲                ▼        ▼
           │       Notification   Fulfillment
           │          consumer      consumer
           │                │        │
           └── processed_messages (dedupe) ──┘
```

## The order lifecycle (state machine)

Every order moves through an explicit, enforced state machine (`OrderStateMachine` + `Order.transitionTo(...)`). The `Order` entity has no public setter for status — the only way to change it is through `transitionTo()`, which checks the transition against an allow-list before applying it and throws `InvalidOrderStateTransitionException` if the move isn't legal. This exists specifically to prevent a class of bug where a retry, a race, or a stray call puts an order into an impossible state (e.g. a `CONFIRMED` order being pushed back to `PAYMENT_PENDING`, which would prompt a customer to pay twice for something already paid for).

```
DRAFT → CREATED → INVENTORY_RESERVATION_PENDING ─┬→ INVENTORY_RESERVED → PAYMENT_PENDING ─┬→ PAYMENT_SUCCEEDED → CONFIRMED
                                                   │                                        ├→ PAYMENT_FAILED → CANCELLED
                                                   └→ INVENTORY_UNAVAILABLE → CANCELLED      └→ PAYMENT_STATUS_UNKNOWN → RECONCILIATION_REQUIRED ─┬→ PAYMENT_SUCCEEDED → CONFIRMED
                                                                                                                                                    └→ PAYMENT_FAILED

CONFIRMED → REFUND_PENDING → REFUNDED
CANCELLATION_REQUESTED → CANCELLED   (unpaid orders)
```

Every transition is recorded as an `OrderAuditEvent` — previous state, new state, actor, correlation ID, and a causation ID that links back to whatever triggered it (a webhook event ID, a gateway payment reference). This is the system's black-box recorder: a support agent can answer "why did this order end up cancelled" months later from this table alone.

## Module responsibilities

**`orders`** — owns the `Order` aggregate and its state machine. Coordinates inventory reservation and delegates to other modules; never performs their work itself.

**`inventory`** — owns stock counts. Reservation is a single atomic `UPDATE ... WHERE available_quantity >= :qty` (not a read-then-write), so concurrent buyers racing for the last unit are resolved by the database, not application code. A separate `InventoryReservation` record tracks each reservation's expiry; a scheduled worker releases stock from reservations nobody completed checkout on.

**`payments`** — talks to a `PaymentGateway` interface (one implementation: `MockPaymentGateway`, which deterministically returns success / decline / timeout based on a test-token prefix). Every payment attempt is written to the database as `PENDING` *before* the gateway is called, and the external call itself happens outside any open database transaction — so a slow or failed gateway call never holds a connection or a lock.

**`reconciliation`** — for orders stuck in `RECONCILIATION_REQUIRED` (gateway call timed out, outcome unknown), a scheduled worker re-queries the gateway by idempotency key and resolves the order. Runs under a lease (`reconciliation_leased_by` / `reconciliation_leased_until` on the `orders` row, acquired with an atomic conditional update) so that if multiple instances of this service were running, only one of them processes a given order.

**`webhooks`** — the push-based counterpart to reconciliation. Verifies an HMAC-SHA256 signature before touching anything, then records the event by its provider-assigned ID under a unique constraint — a duplicate delivery fails that insert and is acknowledged without reapplying any effect. The state-machine check (`OrderStateMachine.canTransition`) is what makes it safe against *out-of-order* delivery: a stale "pending" webhook arriving after the order is already `CONFIRMED` simply finds no valid transition and is a no-op.

**`outbox`** — `OutboxEvent` rows are written in the *same transaction* as the business change that produced them (order confirmation, for instance), so a database commit and the existence of an event to publish can never diverge. A separate scheduled `OutboxPublisher` reads `NEW` events and pushes them to RabbitMQ, retrying on failure and marking an event `FAILED` after 5 attempts.

**`fulfillment` / `notifications`** — independent RabbitMQ consumers, both bound to the same fanout exchange so each receives its own copy of every order event. Both are protected against duplicate delivery by the shared `processed_messages` table (`consumer_name + event_id` as a composite unique key) — the same "try the insert, treat the constraint violation as *already done*" pattern used everywhere else idempotency matters in this system.

**`refunds`** — validates against the sum of previously *succeeded* refunds (computed from the refund records themselves, not a cached running total) before allowing a new one, so cumulative refunds can never exceed what was actually captured.

**`common`** — the idempotency store (`IdempotencyRecord`, keyed so a duplicate request with the same key and payload returns the original result, and a reused key with a different payload is rejected) and Redis-backed rate limiting, both cross-cutting concerns used by every module rather than owned by one.

## Why a database row and not a DB lock for concurrency

Two different concurrency problems show up in this system and are solved two different ways, deliberately:

- **Inventory reservation** uses an atomic conditional `UPDATE` instead of `SELECT ... FOR UPDATE`. The check (`available_quantity >= requested`) is simple enough to express directly in SQL, and holding a row lock across a request under high contention (a flash-sale style burst on one product) would serialize everything through that lock. A conditional update lets Postgres handle it row-by-row without the application holding anything open.
- **The `Order` entity** uses optimistic locking (`@Version`) instead. Its transitions involve multi-step application logic (the state machine) that doesn't reduce to a single `WHERE` clause, so optimistic locking — detect the conflict, let the caller decide what to do — fits better than either a pessimistic lock or a hand-written conditional query.

## Transaction boundaries

A recurring pattern in this codebase: coordinator methods that are *not* `@Transactional` themselves, calling into one or more inner methods that *are*, each with `Propagation.REQUIRES_NEW`. This shows up in `IdempotencyService`, `PaymentService`, `RefundService`, and `ReservationExpiryScheduler`.

The reason is a specific Spring pitfall: if an inner `@Transactional` method throws, Spring marks that transaction *rollback-only* — and if the outer method is sharing the same transaction (the default `REQUIRED` propagation) and catches that exception to keep going, the whole thing still fails to commit at the end with an opaque `UnexpectedRollbackException`. `REQUIRES_NEW` gives the inner step its own transaction, so a handled failure in one step doesn't poison everything built around it. This was found the hard way, during development of the idempotency race-condition handling, not designed in up front — see [decisions.md](decisions.md) for how it surfaced.

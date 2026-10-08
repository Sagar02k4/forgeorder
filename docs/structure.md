# Project Structure

```
forgeorder/
├── docker-compose.yml                   # Postgres, RabbitMQ, Redis
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/sagar/forgeorder/
    │   │   ├── ForgeorderApplication.java
    │   │   │
    │   │   ├── orders/
    │   │   │   ├── domain/        # Order, OrderStatus, OrderStateMachine, OrderAuditEvent, exceptions
    │   │   │   ├── application/   # OrderService, CancellationService
    │   │   │   ├── persistence/   # OrderRepository, OrderAuditEventRepository
    │   │   │   └── api/           # OrderController, request/response DTOs
    │   │   │
    │   │   ├── inventory/
    │   │   │   ├── domain/        # Inventory, InventoryReservation, ReservationStatus
    │   │   │   ├── application/   # InventoryService, ReservationExpiryScheduler
    │   │   │   └── persistence/   # InventoryRepository, InventoryReservationRepository
    │   │   │
    │   │   ├── payments/
    │   │   │   ├── domain/        # PaymentAttempt, PaymentStatus, PaymentGatewayResult
    │   │   │   ├── gateway/       # PaymentGateway (interface), MockPaymentGateway
    │   │   │   ├── application/   # PaymentService
    │   │   │   └── persistence/   # PaymentAttemptRepository
    │   │   │
    │   │   ├── refunds/
    │   │   │   ├── domain/        # Refund, RefundStatus, InvalidRefundException
    │   │   │   ├── application/   # RefundService
    │   │   │   ├── persistence/   # RefundRepository
    │   │   │   └── api/           # Refund request DTO
    │   │   │
    │   │   ├── webhooks/
    │   │   │   ├── domain/        # WebhookEvent, InvalidWebhookSignatureException
    │   │   │   ├── application/   # WebhookService, WebhookSignatureVerifier
    │   │   │   ├── persistence/   # WebhookEventRepository
    │   │   │   └── api/           # WebhookController, WebhookTestController (simulate + inspect gateway state)
    │   │   │
    │   │   ├── reconciliation/
    │   │   │   └── application/   # ReconciliationService, ReconciliationScheduler
    │   │   │
    │   │   ├── outbox/
    │   │   │   ├── domain/        # OutboxEvent, OutboxEventStatus
    │   │   │   ├── application/   # OutboxEventWriter, OutboxPublisher
    │   │   │   └── persistence/   # OutboxEventRepository
    │   │   │
    │   │   ├── fulfillment/
    │   │   │   ├── domain/        # Fulfillment, FulfillmentStatus
    │   │   │   ├── application/   # FulfillmentService, FulfillmentConsumer (RabbitMQ listener)
    │   │   │   └── persistence/   # FulfillmentRepository
    │   │   │
    │   │   ├── notifications/
    │   │   │   └── application/   # NotificationConsumer (RabbitMQ listener)
    │   │   │
    │   │   ├── catalog/
    │   │   │   ├── domain/        # Product, ProductNotFoundException
    │   │   │   └── persistence/   # ProductRepository
    │   │   │
    │   │   ├── operations/
    │   │   │   └── api/           # AdminController
    │   │   │
    │   │   └── common/
    │   │       ├── idempotency/   # IdempotencyRecord, IdempotencyService, IdempotencyOutcome
    │   │       ├── messaging/     # ProcessedMessage, ProcessedMessageTracker (shared consumer dedupe)
    │   │       ├── ratelimit/     # RateLimitService (Redis-backed)
    │   │       └── api/           # ErrorResponse, GlobalExceptionHandler
    │   │
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/      # V1 .. V15, Flyway
    │
    └── test/java/com/sagar/forgeorder/
        ├── orders/                # state machine unit tests, order creation integration tests
        ├── inventory/             # concurrency test (100 threads, 1 unit of stock)
        ├── payments/              # mock gateway unit tests, 3-outcome integration tests
        ├── reconciliation/        # end-to-end timeout → reconcile → confirmed
        └── common/idempotency/    # concurrency test (20 threads, same idempotency key)
```

## Layering rule

Within a module: `api` depends on `application`, `application` depends on `domain` and `persistence`. The reverse never happens — a `domain` entity doesn't know about Spring Data, a `persistence` repository doesn't contain business logic, and a controller never touches a repository directly, only the service.

## What crosses module boundaries, and how

No module reaches into another module's `persistence` package. Cross-module coordination happens by one module's `application` service calling another's — `OrderService` calls `InventoryService.reserveStock(...)`, `CancellationService` calls both `InventoryService.releaseReservation(...)` and `RefundService.initiateRefund(...)`, `WebhookService` and `ReconciliationService` both end up calling into `orders` and `payments`. `common` is the one package every module is allowed to depend on, since idempotency, rate limiting, and the error-response shape are cross-cutting by design rather than belonging to any single module.

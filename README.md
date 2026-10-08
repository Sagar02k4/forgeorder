# ForgeOrder

ForgeOrder is a Spring Boot order-processing service built to explore the parts of checkout that become difficult under concurrency and failure: inventory races, duplicate requests, uncertain payment outcomes, retries, webhooks, and asynchronous fulfillment.

It is intentionally implemented as a modular monolith. The application runs as one process, while each business capability owns its domain model, persistence layer, and application service boundary.

> [!IMPORTANT]
> ForgeOrder uses a mock payment gateway. It is a learning and demonstration project, not a production payment system.

## What it demonstrates

- Atomic inventory reservation without overselling under concurrent requests.
- Idempotent order and payment operations using `Idempotency-Key`.
- An explicit order state machine that rejects invalid transitions.
- Three payment outcomes: success, decline, and unknown status after timeout.
- Reconciliation and signed webhooks for resolving uncertain payments.
- Transactional outbox publishing to RabbitMQ.
- Independent fulfillment and notification consumers with duplicate-message protection.
- Refund validation based on successfully captured amounts.
- Redis-backed rate limiting for order creation.
- Audit history for every order state transition.

## System diagrams

### Architecture
![Architecture diagram](docs/diagrams/architecture.png)

### Data flow
![Data flow diagram](docs/diagrams/dataflow.png)

### Order lifecycle
![Order lifecycle diagram](docs/diagrams/lifecycle.png)

### Request sequence
![Request sequence diagram](docs/diagrams/sequence.png)

### Operational workflow
![Operational workflow diagram](docs/diagrams/workflow.png)

## Quick start

### Prerequisites

- Java 21
- Docker Desktop with Docker Compose
- Maven

Start PostgreSQL, RabbitMQ, and Redis:

```bash
docker compose up -d
```

Set the following environment variable before running the app (required — without it, startup fails on Windows due to a JVM/PostgreSQL timezone-name mismatch):

```
JAVA_TOOL_OPTIONS=-Duser.timezone=UTC
```

Run the application:

```bash
mvn spring-boot:run
```

The service starts on `http://localhost:8080`. PostgreSQL is mapped to host port `5433` (not the default `5432`) to avoid conflicting with any pre-existing local Postgres install. Interactive API documentation is available at:

```text
http://localhost:8080/swagger-ui/index.html
```

Run the automated test suite with:

```bash
mvn test
```

Testcontainers starts the required PostgreSQL and RabbitMQ dependencies for integration tests. See [docs/installation.md](docs/installation.md) for the complete setup and reset procedure.

## A typical checkout flow

1. Create an order with a product ID and quantity. Price and tax are calculated from the catalog on the server.
2. Inventory is reserved with an atomic conditional database update.
3. Start payment using one of the mock tokens documented in [docs/usage.md](docs/usage.md).
4. A successful payment confirms the order and publishes an outbox event.
5. Notification and fulfillment consumers independently receive the confirmation event.

For a timeout scenario, use a `tok_timeout_*` token. The order enters `RECONCILIATION_REQUIRED`; the scheduled worker or the admin reconcile endpoint then queries the gateway by idempotency key and resolves the final outcome.

## API highlights

| Operation | Endpoint | Purpose |
|---|---|---|
| Create order | `POST /api/orders` | Create and reserve inventory for an order |
| Read order | `GET /api/orders/{id}` | Return order, inventory, and latest payment details |
| Start payment | `POST /api/orders/{id}/payments` | Initiate payment against a reserved order |
| Cancel order | `POST /api/orders/{id}/cancel` | Release inventory or initiate a refund |
| Refund order | `POST /api/orders/{id}/refunds` | Initiate a validated full refund |
| Payment webhook | `POST /api/webhooks/mock-payment` | Apply a signed asynchronous gateway event |
| List operations | `GET /api/admin/orders` | Find orders by status |
| Reconcile order | `POST /api/admin/orders/{id}/reconcile` | Resolve an unknown payment immediately |

All mutating endpoints require an `Idempotency-Key`. An optional `X-Correlation-ID` can be supplied for request tracing.

## Design principles

### Concurrency is resolved at the write boundary

Inventory uses `UPDATE ... WHERE available_quantity >= :quantity`, so competing buyers cannot both reserve the last unit. Idempotency records, webhook events, and processed messages use the same insert-and-handle-the-constraint pattern.

### Unknown payment status is not treated as failure

A gateway timeout does not prove that a charge failed. ForgeOrder preserves that uncertainty and resolves it through reconciliation or a signed webhook instead of cancelling a potentially successful payment.

### Database state and events commit together

Business changes and their outbox records are written in the same transaction. A publisher later delivers those records to RabbitMQ, with retries and a terminal failed state after five attempts.

### State changes are named operations

Domain entities do not expose public status setters. Transitions go through methods such as `transitionTo`, `markSucceeded`, and `markFailed`, keeping illegal states out of the application.

## Documentation

The detailed documentation is intended to live under `docs/`:

- [Architecture and lifecycle](docs/architecture.md) — modules, state machine, transaction boundaries, and concurrency choices.
- [Project structure](docs/structure.md) — package layout and layering rules.
- [API reference](docs/api.md) — endpoints, request/response shapes, headers, and errors.
- [Installation](docs/installation.md) — infrastructure, JVM settings, startup, reset, and tests.
- [Usage guide](docs/usage.md) — seed data, mock payment tokens, and failure-mode walkthroughs.
- [Testing](docs/testing.md) — unit, integration, concurrency, and end-to-end coverage.
- [Technical decisions](docs/decisions.md) — the reasoning behind the major design choices.

Together, the architecture, structure, API, testing, and usage documents provide the system diagrams and operational walkthroughs around this README.

## Technology stack

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC and Bean Validation
- Spring Data JPA with PostgreSQL
- Flyway database migrations
- RabbitMQ with Spring AMQP
- Redis for rate limiting
- Springdoc OpenAPI
- JUnit, Spring Boot Test, and Testcontainers

## Project status and boundaries

ForgeOrder currently has no authentication or authorization layer, so admin endpoints must be treated as development-only endpoints. Prometheus registry support is present as a dependency, but `/actuator/prometheus` is not exposed yet. Webhook signature/duplicate-delivery checks and rate-limiter behavior are documented and manually verifiable, while load testing under real HTTP traffic is outside the current automated suite.

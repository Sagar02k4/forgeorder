# Installation

## Prerequisites

- Java 21
- Maven
- Docker + Docker Compose

## 1. Clone and start infrastructure

```bash
git clone <repo-url>
cd forgeorder
docker compose up -d
```

This starts three containers:

| Service | Port(s) | Purpose |
|---|---|---|
| PostgreSQL 16 | `5433` (host) → `5432` (container) | source of truth |
| RabbitMQ 3 (management) | `5672` (AMQP), `15672` (UI) | event delivery |
| Redis 7 | `6379` | rate limiting |

> Postgres is mapped to host port `5433`, not the default `5432`. This is deliberate — on Windows in particular, a pre-existing local Postgres install commonly already occupies `5432`, and two processes on the same port silently causes connection failures that look like a credentials problem. Using `5433` avoids that entirely rather than requiring you to find and stop whatever else is listening.

Verify the containers are healthy:

```bash
docker ps
```

All three should show `Up`.

## 2. JVM timezone

Set a system environment variable before running the app:

```
JAVA_TOOL_OPTIONS=-Duser.timezone=UTC
```

**Why this is required:** on Windows, the JVM reports the system timezone using a legacy name (`Asia/Calcutta` instead of `Asia/Kolkata`), which PostgreSQL's JDBC driver rejects outright at connection time (`FATAL: invalid value for parameter "TimeZone"`). Setting this at the JVM level — rather than per-connection or per-run-configuration — is what makes it apply consistently whether the app is started from IntelliJ, from `mvn spring-boot:run`, or as a test.

## 3. Run the application

```bash
mvn spring-boot:run
```

On startup, Flyway applies all 15 migrations and seeds three test products with stock. Look for:

```
Schema "public" is up to date
Started ForgeorderApplication in ...
```

## 4. Verify

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- RabbitMQ management UI: `http://localhost:15672` (user: `forgeorder_user`, password: `forgeorder_pass`)
- Health check: `GET http://localhost:8080/actuator/health`

## Resetting to a clean state

Since test products, inventory, and any reservations/orders from manual testing accumulate in the same database, reset with:

```bash
docker compose down -v
docker compose up -d
```

The `-v` flag drops the Docker volume, so Postgres reinitializes from scratch and Flyway re-seeds the original test data on next startup. See [usage.md](usage.md) for what that seed data is.

## Running the test suite

```bash
mvn test
```

Integration and concurrency tests spin up their own PostgreSQL container via Testcontainers — they do not touch the database started by `docker compose`. Docker must be running for these to pass. See [testing.md](testing.md) for what each test class covers.

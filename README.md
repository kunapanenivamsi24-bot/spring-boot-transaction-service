# spring-boot-transaction-service

A Spring Boot REST service for managing accounts and financial transactions —
credits, debits, balance tracking, idempotent transaction processing, JWT
authentication, caching, and async post-processing.

> **Portfolio demo disclaimer:** This is a personal portfolio demo project built
> by Sai Sasi Vamsi Kunapaneni to showcase this stack. It is **not** affiliated
> with, endorsed by, or derived from the code of Morgan Stanley or any other
> employer. All data is synthetic demo data.
>
> LinkedIn: https://www.linkedin.com/in/vamsi-kunapaneni-6ab48831a/

## Features

- **Accounts** — create and list accounts, fetch by id (Caffeine-cached).
- **Transactions** — credit/debit with balance validation; insufficient-funds
  debits are rejected with HTTP 422 and never touch the balance.
- **Idempotency** — every transaction carries an idempotency key (header
  `Idempotency-Key` or the `idempotencyKey` body field). Replaying a key
  returns the original transaction instead of applying it twice.
- **JWT security** — stateless Spring Security with a JJWT-based
  `Authorization: Bearer <token>` filter. Demo user: `demo` / `demo123`
  (demo-only credentials — replace in any real deployment).
- **Caching** — account lookups cached with Caffeine (10-minute TTL, max 1000
  entries).
- **Async processing** — transaction notifications/audit run on a dedicated
  thread pool (`tx-async-`, core 2 / max 5 / queue 100). The demo only logs
  them; no external service is called.
- **Persistence** — H2 in-memory by default; PostgreSQL via the `postgres`
  profile. Flyway-free, JPA `ddl-auto` managed schema.

## Tech stack

Java 17 · Spring Boot 3.2.5 · Spring Web / Data JPA / Security / Validation /
Cache · Caffeine · H2 · PostgreSQL · JJWT 0.12.6 · Maven · JUnit 5 + Mockito ·
Docker / Docker Compose · GitHub Actions

## Prerequisites

- Java 17+ and Maven 3.9+ (or Docker, for the containerised run)

## Run locally (H2)

```bash
mvn spring-boot:run
```

The API is available at `http://localhost:8080`. The H2 console is at
`http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:txdb`, user `sa`).

## Run with Docker Compose (PostgreSQL)

```bash
docker compose up --build
```

This starts the app with the `postgres` Spring profile against a Postgres 16
container (database `txdb`, user `txuser`). The app waits for Postgres to be
healthy before starting.

## API

All `/api/**` endpoints except `/api/auth/**` require a JWT.

| Method | Endpoint | Description |
| ------ | -------- | ----------- |
| POST | `/api/auth/login` | Log in, returns a JWT |
| POST | `/api/accounts` | Create an account |
| GET | `/api/accounts` | List accounts |
| GET | `/api/accounts/{id}` | Get an account by id |
| POST | `/api/transactions` | Create a transaction (credit/debit) |
| GET | `/api/accounts/{id}/transactions?page=0&size=20` | List an account's transactions (paged) |

### Examples

Log in and save the token:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"demo123"}' | python3 -c 'import sys, json; print(json.load(sys.stdin)["token"])')
```

Create an account:

```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"accountNumber":"ACC-001","ownerName":"Ada Lovelace","initialBalance":1000.00,"currency":"USD"}'
```

Post a transaction (note the `Idempotency-Key` header):

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: tx-2026-11-09-001' \
  -d '{"accountId":1,"type":"DEBIT","amount":250.00,"currency":"USD","description":"Rent","idempotencyKey":"tx-2026-11-09-001"}'
```

Replay the exact same request — the response is the original transaction and
the balance is debited only once:

```bash
curl -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: tx-2026-11-09-001' \
  -d '{"accountId":1,"type":"DEBIT","amount":250.00,"currency":"USD","description":"Rent","idempotencyKey":"tx-2026-11-09-001"}'
```

Error responses are JSON with `timestamp`, `status`, `error`, and `message`.
Validation failures return 400, missing resources 404, and insufficient funds
422.

## Tests

```bash
mvn test
```

Unit tests (JUnit 5 + Mockito, no database or Docker required) cover the
transaction service — credit increases the balance, insufficient-funds debits
are rejected without changing the balance, and a duplicate idempotency key
returns the original transaction without double-applying — plus the account
service.

## Configuration

| Property | Default | Description |
| -------- | ------- | ----------- |
| `server.port` | `8080` | HTTP port |
| `app.jwt.secret` | demo Base64 value | JWT signing secret (**demo-only** — set `APP_JWT_SECRET` in production) |
| `app.jwt.expiration` | `3600000` (1h) | JWT lifetime in ms |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5432/txdb` / `txuser` / `txpass` | Postgres datasource (postgres profile) |

## Production notes

This is a demo. Before production use it would need, at minimum: a real user
store and secret management (no demo credentials or hard-coded secret),
row-level (pessimistic) locking on the account row inside the transaction to
prevent concurrent balance races, a persistent idempotency store with TTL,
and rate limiting / audit logging.

## License

MIT — see [LICENSE](LICENSE).

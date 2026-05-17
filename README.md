# Java DDD Clean Architecture — Microservices

A Spring Boot microservices project built with Domain-Driven Design (DDD) and Clean Architecture principles, running on Java 21.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Option A — Run Locally (Development)](#option-a--run-locally-development)
- [Option B — Run with Docker Compose (Production)](#option-b--run-with-docker-compose-production)
- [Service Reference](#service-reference)
- [API Reference](#api-reference)
- [Security](#security)
  - [Invoice File Integrity (SHA-256)](#invoice-file-integrity-sha-256)
- [RabbitMQ — Event-Driven Messaging](#rabbitmq--event-driven-messaging)
- [Running Tests](#running-tests)

---

## Architecture Overview

```
                        ┌─────────────────────────┐
  Browser / Client ────▶│   API Gateway (:8080)   │
                        │  Spring Cloud Gateway   │
                        │  JWT auth · CircuitBreaker│
                        └──────────┬──────────────┘
                                   │ lb:// (Eureka)
          ┌──────────────┬─────────┴────┬──────────────────┐
          │              │              │                  │
┌─────────▼──────┐  ┌────▼────────┐  ┌─▼──────────────┐  ┌▼────────────────────┐
│  MSR Service   │  │ HCP Service │  │  Visit Service │  │  Settlement Service │
│   (:8086)      │  │  (:8087)    │  │   (:8088)      │  │   (:8089)           │
│  PostgreSQL    │  │  PostgreSQL │  │  SQL Server    │  │  MySQL              │
└────────────────┘  └─────────────┘  └────────────────┘  └─────────────────────┘
                        ┌─────────────────────────┐
                        │  Identity Service(:8090) │
                        │  JWT issuance · BCrypt   │
                        └─────────────────────────┘
                        ┌─────────────────────────┐
                        │  Eureka Server (:8761)  │
                        └─────────────────────────┘
```

Each microservice follows a three-layer Clean Architecture:

| Layer | Module | Responsibility |
|---|---|---|
| Application | `*-application` | HTTP controllers, security config, Spring Boot entry point |
| Domain | `*-domain` | Entities, use-cases, repository interfaces, domain events |
| Infrastructure | `*-infra` | JPA repositories, HTTP clients, RabbitMQ publishers |

The `domain-commons` module provides shared DDD building blocks (`AggregateRoot`, `ValueObject`, `Criteria`, exception hierarchy) used by all domain modules.

---

## Prerequisites

| Tool | Version |
|---|---|
| Java (JDK) | 21 |
| Maven | 3.9+ |
| Docker & Docker Compose | 24+ (for Option B or local databases) |
| Git | any |

Verify:

```bash
java -version    # openjdk 21
mvn -version     # Apache Maven 3.9.x
docker --version # Docker 24.x
```

---

## Project Structure

```
.
├── domain-commons/             # Shared DDD building blocks
├── eureka-server/              # Service registry (Spring Cloud Netflix Eureka)
├── api-gateway/                # Reactive gateway (Spring Cloud Gateway)
├── identity-service/           # Centralised authentication & JWT issuance
│   ├── identity-application/   # Spring Boot app, AuthController, security config
│   ├── identity-domain/        # User entity, ports, LoginUseCase, DTOs
│   └── identity-infra/         # BCrypt adapter, JWT adapter, in-memory user store
├── medical-sales-rep-service/
│   ├── msr-application/        # Spring Boot app, controllers, security
│   ├── msr-domain/             # Entities, use-cases, interfaces
│   └── msr-infra/              # JPA repos, HTTP clients
├── healthcare-prof-service/
│   ├── hcp-application/
│   ├── hcp-domain/
│   └── hcp-infra/
├── visit-service/
│   ├── visit-application/
│   ├── visit-domain/
│   └── visit-infra/
├── settlement-service/
│   ├── settlement-application/
│   ├── settlement-domain/
│   └── settlement-infra/
├── docker-compose.yml          # Full stack (prod profile)
├── .env.example                # Environment variable template
└── pom.xml                     # Parent POM
```

---

## Configuration

Each service reads configuration from `application.properties` (dev defaults) or `application-prod.properties` (all values required via environment variables, no fallbacks).

### Environment Variables

Copy `.env.example` to `.env` and fill in your values before running Docker Compose:

```bash
cp .env.example .env
```

| Variable | Used by | Description |
|---|---|---|
| `JWT_SECRET` | Gateway, all services | Shared JWT signing secret (≥ 32 chars) |
| `EUREKA_USER` | Eureka, all services | Eureka basic-auth username |
| `EUREKA_PASSWORD` | Eureka, all services | Eureka basic-auth password |
| `EUREKA_URL` | All services (prod) | Full Eureka URL with credentials |
| `CORS_ALLOWED_ORIGINS` | Gateway | Comma-separated allowed origins |
| `MSR_DB_URL` | MSR service | PostgreSQL JDBC URL for `medicalsalesrep_db` |
| `MSR_PG_USERNAME` | MSR service | DB username |
| `MSR_PG_PASSWORD` | MSR service | DB password |
| `HCP_DB_URL` | HCP service | PostgreSQL JDBC URL for `healthcare_db` |
| `HCP_PG_USERNAME` | HCP service | DB username |
| `HCP_PG_PASSWORD` | HCP service | DB password |
| `VISIT_DB_URL` | Visit service | SQL Server JDBC URL for `visitdb` (e.g. `jdbc:sqlserver://localhost:1433;databaseName=visitdb;encrypt=false;trustServerCertificate=true`) |
| `VISIT_DB_USERNAME` | Visit service | DB username |
| `VISIT_DB_PASSWORD` | Visit service | DB password |
| `SETTLEMENT_DB_URL` | Settlement service | MySQL JDBC URL for `settlementdb` |
| `SETTLEMENT_DB_USERNAME` | Settlement service | DB username |
| `SETTLEMENT_DB_PASSWORD` | Settlement service | DB password |
| `RABBITMQ_HOST` | All services | RabbitMQ hostname |
| `RABBITMQ_USERNAME` | All services | RabbitMQ username |
| `RABBITMQ_PASSWORD` | All services | RabbitMQ password |
| `INVOICE_FILE_STORAGE_PATH` | Settlement service | Absolute path where digital invoice files are stored on disk (default: `/var/settlement-service/invoice-files`) |

### Dev defaults (no `.env` needed)

The `application.properties` files ship with working dev defaults so services start without a `.env`:

| Service | Database | Dev credentials |
|---|---|---|
| MSR | PostgreSQL `localhost:5433/medicalsalesrep_db` | `root` / `river` |
| HCP | PostgreSQL `localhost:5433/healthcare_db` | `root` / `river` |
| Visit | SQL Server `localhost:1433/visitdb` | `sa` / (set via `DB_PASSWORD` env var, e.g. `Riverplate1!`) |
| Settlement | MySQL `localhost:3306/settlementdb` | `root` / (your MySQL root password) |

---

## Option A — Run Locally (Development)

### 1. Start the databases

**PostgreSQL** (shared container for MSR and HCP):

```bash
# Create and start
docker run -d --name postgres-ddd-clean \
  -p 5433:5432 \
  -e POSTGRES_USER=root \
  -e POSTGRES_PASSWORD=river \
  -e POSTGRES_DB=healthcare_db \
  -v postgres_data:/var/lib/postgresql/data \
  postgres:latest

# Create the second database inside the same container
docker exec -it postgres-ddd-clean \
  psql -U root -d postgres -c "CREATE DATABASE medicalsalesrep_db;"

# To restart later
docker start postgres-ddd-clean
```

**Microsoft SQL Server** (Visit Service):

```bash
docker run -e "ACCEPT_EULA=Y" \
           -e "MSSQL_SA_PASSWORD=Riverplate1!" \
           -p 1433:1433 \
           --name sqlserver_ddd_clean \
           -d mcr.microsoft.com/mssql/server:2022-latest

# Wait ~15 s for SQL Server to be ready, then create the database
# (-No suppresses the TLS certificate warning on the 2022 image)
docker exec -it sqlserver_ddd_clean /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "Riverplate1!" -No \
  -Q "CREATE DATABASE visitdb"

# To restart later
docker start sqlserver_ddd_clean
```

**MySQL** (Settlement Service only):

```bash
docker run -d --name mysql-ddd-clean \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=yourpassword \
  -e MYSQL_DATABASE=settlementdb \
  -v mysql_data:/var/lib/mysql \
  mysql:8

# To restart later
docker start mysql-ddd-clean
```

**RabbitMQ**:

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management

# Management UI: http://localhost:15672  (guest / guest)
```

### 2. Build all modules

Run once from the repo root to install shared modules (`domain-commons`, etc.) into your local Maven repository:

```bash
mvn clean install -DskipTests
```

### 3. Start the services

Open a separate terminal for each service. Start in this order:

**Terminal 1 — Eureka Server**

```bash
mvn -pl eureka-server -am spring-boot:run
# Ready when: "Started EurekaServerApplication" appears
# Dashboard: http://localhost:8761 (eureka, eureka)
```

**Terminal 2 — API Gateway**

```bash
mvn -pl api-gateway -am spring-boot:run
# Ready when: "Started GatewayApplication" appears
# Listens on: http://localhost:8080
```

**Terminal 3 — Identity Service**

```bash
mvn -pl identity-service/identity-application -am spring-boot:run
# Ready when: "Started IdentityApplication" appears
# Auth endpoint: POST http://localhost:8090/auth/login
```

**Terminals 4–7 — Microservices (any order)**

```bash
# Medical Sales Rep Service
mvn -pl medical-sales-rep-service/msr-application -am spring-boot:run

# Healthcare Prof Service
mvn -pl healthcare-prof-service/hcp-application -am spring-boot:run

# Visit Service (DB_PASSWORD passes the SQL Server SA password)
DB_PASSWORD='Riverplate1!' mvn -pl visit-service/visit-application -am spring-boot:run --no-transfer-progress

# Settlement Service
mvn -pl settlement-service/settlement-application -am spring-boot:run
```

All commands run from the repo root.

### 4. Verify all services are registered

Open the Eureka dashboard at `http://localhost:8761`. You should see all four microservices listed as `UP`.

### 5. Obtain a JWT token

Authentication is centralised in the **Identity Service** (`identity-service/identity-application`). Obtain a token directly or via the gateway — both routes resolve to the same service:

```bash
# Via API Gateway (recommended)
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"Apatehia65$"}'

# Directly against the Identity Service
curl -s -X POST http://localhost:8090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"Apatehia65$"}'
```

Use the returned token in subsequent requests:

```bash
curl -s "http://localhost:8080/api/v1/medicalsalesrep/get?id=<uuid>" \
  -H "Authorization: Bearer <token>"
```

---

## Option B — Run with Docker Compose (Production)

All services run as Docker containers on an internal network. Only the API Gateway port is published to the host.

### 1. Fill in secrets

```bash
cp .env.example .env
# Edit .env — replace every "change-me" value with a real secret
```

### 2. Build and start

```bash
docker compose up -d --build
```

This starts:

| Container | Published port | Profile |
|---|---|---|
| `eureka-server` | none (internal only) | `prod` |
| `api-gateway` | `8080` | `prod` |
| `identity-service` | none | `prod` |
| `medical-sales-rep-service` | none | `prod` |
| `healthcare-prof-service` | none | `prod` |
| `visit-service` | none | `prod` |
| `settlement-service` | none | `prod` |

> In prod mode Swagger UI is disabled on all services. All secrets are required — missing env vars will cause the service to fail to start.

### 3. Check status

```bash
docker compose ps
docker compose logs -f api-gateway
```

### 4. Stop

```bash
docker compose down
```

---

## Service Reference

| Service | Port (dev) | Module path | Database |
|---|---|---|---|
| Eureka Server | 8761 | `eureka-server` | — |
| API Gateway | 8080 | `api-gateway` | — |
| Identity Service | 8090 | `identity-service/identity-application` | — (in-memory) |
| Medical Sales Rep | 8086 | `medical-sales-rep-service/msr-application` | PostgreSQL `medicalsalesrep_db` |
| Healthcare Prof | 8087 | `healthcare-prof-service/hcp-application` | PostgreSQL `healthcare_db` |
| Visit | 8088 | `visit-service/visit-application` | SQL Server `visitdb` |
| Settlement | 8089 | `settlement-service/settlement-application` | MySQL `settlementdb` |
| RabbitMQ | 5672 / 15672 | external (user-managed) | — |

---

## API Reference

All endpoints are accessible via the **API Gateway** at `http://localhost:8080`. Direct service ports are bound to `127.0.0.1` in dev (loopback only).

### Authentication — Identity Service (`/auth`)

All authentication is handled by the **Identity Service**. The endpoint is exposed directly on port `8090` and proxied through the API Gateway at `http://localhost:8080/auth/login`.

Dev credential: `user` / `Apatehia65$`

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/login` | Returns a JWT bearer token |

Request body:
```json
{ "username": "user", "password": "Apatehia65$" }
```

Example response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "user",
  "roles": ["ROLE_USER"]
}
```

Include the token in all subsequent requests:
```
Authorization: Bearer <token>
```

---

### Medical Sales Rep Service — `/api/v1/medicalsalesrep`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/medicalsalesrep/create` | Create a new medical sales rep |
| `PUT` | `/api/v1/medicalsalesrep/update` | Update an existing rep |
| `POST` | `/api/v1/medicalsalesrep/activate` | Activate a rep |
| `POST` | `/api/v1/medicalsalesrep/deactivate` | Deactivate a rep |
| `GET` | `/api/v1/medicalsalesrep/get?id=<uuid>` | Get a rep by ID |
| `POST` | `/api/v1/medicalsalesrep/list` | List reps (with criteria filter) |

---

### Healthcare Prof Service — `/api/v1/healthcareprof`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/healthcareprof/create` | Create a new healthcare professional |
| `PUT` | `/api/v1/healthcareprof/update` | Update an existing HCP |
| `POST` | `/api/v1/healthcareprof/activate` | Activate an HCP |
| `POST` | `/api/v1/healthcareprof/deactivate` | Deactivate an HCP |
| `GET` | `/api/v1/healthcareprof/get?id=<uuid>` | Get an HCP by ID |
| `POST` | `/api/v1/healthcareprof/list` | List HCPs (with criteria filter) |
| `GET` | `/api/v1/healthcareprof/specialties` | List available specialties |

---

### Visit Service — `/api/v1/visit` and `/api/v1/visitplan`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/visit/create` | Create a visit |
| `PUT` | `/api/v1/visit/update` | Update a visit |
| `GET` | `/api/v1/visit/get?id=<uuid>` | Get a visit by ID |
| `POST` | `/api/v1/visit/list` | List visits (with criteria filter) |
| `POST` | `/api/v1/visitplan/create` | Create a visit plan |
| `PUT` | `/api/v1/visitplan/update` | Update a visit plan |
| `GET` | `/api/v1/visitplan/get?id=<uuid>` | Get a visit plan by ID |
| `POST` | `/api/v1/visitplan/list` | List visit plans (with criteria filter) |

---

### Settlement Service — `/api/v1/settlement`

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/settlement/create` | Create a settlement |
| `PUT` | `/api/v1/settlement/update` | Update a settlement |
| `GET` | `/api/v1/settlement/get?id=<uuid>` | Get a settlement by ID |
| `POST` | `/api/v1/settlement/list` | List settlements (with criteria filter) |
| `POST` | `/api/v1/settlement/invoice/upload` | Upload invoice file (`multipart/form-data`) — params: `settlementId`, `invoiceId`, part: `file` |

---

## Security

### Authentication & Authorization

- **Token issuance** is centralised in `identity-service` (`POST /auth/login`, port `8090`).
- JWT bearer tokens are validated by the API Gateway before routing to any downstream service.
- Each microservice also validates the token independently (defence in depth).
- Tokens are signed with a shared `JWT_SECRET` (≥ 32 chars, HS256). **Rotate this secret in production.**
- The `/auth/**` and `/error` paths are public; all other paths require a valid token.
- Passwords are hashed with BCrypt (cost factor 12) — OWASP A02 compliant.
- All login attempts (success and failure) are written to `AUDIT` level logs with the sanitized username.

### CORS

The gateway allows only the origins listed in `CORS_ALLOWED_ORIGINS` (defaults to `http://localhost:5173` and `http://localhost:3000` in dev). Set a specific production origin in `.env`.

### Security Headers

All responses include:
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Strict-Transport-Security` (HSTS)
- `Referrer-Policy: no-referrer`

### Network Isolation

- In dev, services bind to `127.0.0.1` only — they are not reachable from outside the host.
- In Docker Compose, only the API Gateway publishes a port (`8080`). All other services are on an internal Docker network with no published ports.

### Audit Logging

All `/auth/login` attempts (success and failure) are logged at the `AUDIT` level with the sanitized username and resolved client IP.

### Production Checklist

- [ ] Replace all `change-me` values in `.env`
- [ ] Use a random JWT secret of at least 32 characters (`openssl rand -base64 32`)
- [ ] Run behind TLS (reverse proxy such as nginx or a load balancer)
- [ ] Set `CORS_ALLOWED_ORIGINS` to your frontend domain only
- [ ] Confirm `SPRING_PROFILES_ACTIVE=prod` is set in your deployment

### Dependency Vulnerability Scan

```bash
mvn org.owasp:dependency-check-maven:check
```

### Invoice File Integrity (SHA-256)

The **Settlement Service** stores each invoice's digital file (e.g. a PDF) in two places:

| What | Where |
|---|---|
| File metadata (`fileName`, `contentType`, `sizeInBytes`, `sha256Hash`) | MySQL — `invoices` table |
| Binary content | Local file system — `$INVOICE_FILE_STORAGE_PATH/<invoiceId>/<fileName>` |

A **SHA-256 digest** of the binary content is computed eagerly when an `InvoiceFile` value object is constructed and is persisted to MySQL alongside the other metadata.  When the file is later loaded from disk, the digest is recomputed and compared against the stored value.  A mismatch raises a `FileIntegrityException` immediately, before the bytes are returned to the domain.

**Why SHA-256 instead of MD5 or SHA-1?**  MD5 and SHA-1 are broken for collision resistance and must not be used for integrity checks.  SHA-256 is mandated by NIST SP 800-107 and is available as a standard JVM algorithm with no extra dependencies.

**Benefits:**

- **Data integrity** — silent disk corruption or partial writes are detected on the next read.
- **Tamper detection** — if someone modifies the file directly on the file system, the hash mismatch is caught before the corrupted bytes reach the domain layer (OWASP A08 — Software and Data Integrity).
- **Idempotency signal** — callers can compare `sha256Hash()` values to detect whether two `InvoiceFile` instances carry identical content without reading all bytes.
- **Audit trail** — the stored hash in MySQL provides a permanent, queryable fingerprint of the original file independent of the file system.

**Architecture layering:**

```
domain-commons  FileValueObject      ← computes SHA-256 eagerly at construction
                                        (generic — available to any bounded context)
settlement-domain  InvoiceFile       ← extends FileValueObject; adds settlement-
                                        specific rules (10 MB cap, extension, MIME)
settlement-domain  IInvoiceFileStorage  ← output port; loadContent() accepts
                                           expectedHash — verification is a
                                           first-class contract obligation
settlement-infra  LocalDiskInvoiceFileStorage  ← recomputes hash after reading
                                                  bytes; throws FileIntegrityException
                                                  on mismatch
settlement-infra  InvoiceEntity      ← invoiceFileHash column (VARCHAR 64) in MySQL
```

The domain layer knows nothing about disk or hashing algorithms — it only defines the abstraction (`IInvoiceFileStorage`) and the exception type (`FileIntegrityException`).  All cryptographic I/O lives in the infrastructure layer.

---

## RabbitMQ — Event-Driven Messaging

RabbitMQ provides an optional **asynchronous messaging layer** between microservices. It decouples the Medical Sales Rep and Healthcare Prof services from the Visit Service, allowing the Visit Service to maintain local read-model snapshots of MSR and HCP data without making synchronous HTTP calls at query time.

> **Note:** Publishing is disabled in the `dev` Spring profile for the MSR service (`@Profile("!dev")` on `MsrAmqpEventPublisher`). To enable event publishing, run with a profile other than `dev` (e.g. `prod`) and have a running RabbitMQ instance.

### Message Topology

**Publishers**

| Exchange | Type | Published by | Routing keys |
|---|---|---|---|
| `msr.events` | Topic | MSR Service (`msr-infra/MsrAmqpEventPublisher`) | `msr.created`, `msr.updated`, `msr.activated`, `msr.deactivated` |
| `hcp.events` | Topic | HCP Service (`hcp-infra/HcpAmqpEventPublisher`) | `hcp.created`, `hcp.updated`, `hcp.activated`, `hcp.deactivated` |

**Consumers**

| Queue | Bound exchange | Routing pattern | Consumed by |
|---|---|---|---|
| `visit-service.msr.queue` | `msr.events` | `msr.#` | Visit Service — `MsrSnapshotUpdater` |
| `visit-service.hcp.queue` | `hcp.events` | `hcp.#` | Visit Service — `HcpSnapshotUpdater` |

All queues and exchanges are **durable**. Messages are serialized as **JSON** (`Jackson2JsonMessageConverter`).

### What the consumers do

- **`MsrSnapshotUpdater`** — listens on `visit-service.msr.queue` and upserts a local `msr_snapshot` row in the Visit Service's MySQL database. `MSR_CREATED` / `MSR_UPDATED` events trigger a full upsert; `MSR_ACTIVATED` / `MSR_DEACTIVATED` events flip the `active` flag only.
- **`HcpSnapshotUpdater`** — same pattern on `visit-service.hcp.queue`, keeping a local `hcp_snapshot` table current for HCP events.

These local snapshots remove the synchronous HTTP dependency on MSR/HCP services when the Visit Service creates or queries visits.

### Running RabbitMQ locally (Option A)

The `docker run` command is shown in the [Option A — Run Locally](#option-a--run-locally-development) section. The management console is available at:

```
http://localhost:15672   (default credentials: guest / guest)
```

### RabbitMQ in Docker Compose (Option B)

RabbitMQ is **not** bundled in `docker-compose.yml` — it is expected to be an externally managed service (e.g. a managed CloudAMQP instance or a dedicated Docker container on the same network). Set the three required env vars in `.env`:

```
RABBITMQ_HOST=<hostname>
RABBITMQ_USERNAME=<username>
RABBITMQ_PASSWORD=<password>
```

The MSR, HCP, and Visit services read these variables. The Settlement Service does not use RabbitMQ.

---

## Running Tests

```bash
# All modules
mvn test

# Single module
mvn -pl medical-sales-rep-service/msr-domain test
mvn -pl healthcare-prof-service/hcp-domain test
mvn -pl visit-service/visit-domain test
mvn -pl settlement-service/settlement-domain test
```

Test reports are written to `<module>/target/surefire-reports/`.

---

## License

Licensed under the MIT License. See [LICENSE](LICENSE) for details.

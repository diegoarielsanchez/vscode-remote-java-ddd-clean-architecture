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
│  PostgreSQL    │  │  PostgreSQL │  │  MySQL         │  │  MySQL              │
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
| `VISIT_DB_URL` | Visit service | MySQL JDBC URL for `visitdb` |
| `VISIT_DB_USERNAME` | Visit service | DB username |
| `VISIT_DB_PASSWORD` | Visit service | DB password |
| `SETTLEMENT_DB_URL` | Settlement service | MySQL JDBC URL for `settlementdb` |
| `SETTLEMENT_DB_USERNAME` | Settlement service | DB username |
| `SETTLEMENT_DB_PASSWORD` | Settlement service | DB password |
| `RABBITMQ_HOST` | All services | RabbitMQ hostname |
| `RABBITMQ_USERNAME` | All services | RabbitMQ username |
| `RABBITMQ_PASSWORD` | All services | RabbitMQ password |

### Dev defaults (no `.env` needed)

The `application.properties` files ship with working dev defaults so services start without a `.env`:

| Service | Database | Dev credentials |
|---|---|---|
| MSR | PostgreSQL `localhost:5433/medicalsalesrep_db` | `root` / `river` |
| HCP | PostgreSQL `localhost:5433/healthcare_db` | `root` / `river` |
| Visit | MySQL `localhost:3306/visitdb` | `root` / (your MySQL root password) |
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

**MySQL** (shared container for Visit and Settlement):

```bash
docker run -d --name mysql-ddd-clean \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=yourpassword \
  -e MYSQL_DATABASE=visitdb \
  -v mysql_data:/var/lib/mysql \
  mysql:8

# Create the second database
docker exec -it mysql-ddd-clean \
  mysql -u root -pyourpassword -e "CREATE DATABASE IF NOT EXISTS settlementdb;"

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

# Visit Service
mvn -pl visit-service/visit-application -am spring-boot:run

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
| Visit | 8088 | `visit-service/visit-application` | MySQL `visitdb` |
| Settlement | 8089 | `settlement-service/settlement-application` | MySQL `settlementdb` |

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

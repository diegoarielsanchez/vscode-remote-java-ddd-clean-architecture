# Java DDD Clean Architecture — Microservices

A Spring Boot microservices project built with Domain-Driven Design (DDD) and Clean Architecture principles, running on Java 25.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Prerequisites](#prerequisites)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Option A — Run Locally (Development)](#option-a--run-locally-development)
- [Option C — Run Individual Docker Containers](#option-c--run-individual-docker-containers)
- [Option B — Run with Docker Compose (Production)](#option-b--run-with-docker-compose-production)
  - [1. Fill in secrets](#1-fill-in-secrets)
  - [2. Create the external Docker network](#2-create-the-external-docker-network)
  - [3. Build and start](#3-build-and-start)
  - [4. Check status](#4-check-status)
  - [5. Verify all services registered with Eureka](#5-verify-all-services-registered-with-eureka)
  - [6. Obtain a JWT token](#6-obtain-a-jwt-token)
  - [7. Dev override — MinIO file storage (optional)](#7-dev-override--minio-file-storage-optional)
  - [8. Stop](#8-stop)
  - [9. Rebuild a single service](#9-rebuild-a-single-service)
- [Service Reference](#service-reference)
- [API Reference](#api-reference)
- [Swagger UI](#swagger-ui)
- [Security](#security)
  - [Invoice File Integrity (SHA-256)](#invoice-file-integrity-sha-256)
- [RabbitMQ — Event-Driven Messaging](#rabbitmq--event-driven-messaging)
- [CI with Jenkins](#ci-with-jenkins)
  - [1. Install required plugins](#1-install-required-plugins)
  - [2. Configure tools](#2-configure-tools)
  - [3. Create a Pipeline job for each service](#3-create-a-pipeline-job-for-each-service)
  - [4. Pipeline stages](#4-pipeline-stages)
  - [5. OWASP controls summary](#5-owasp-controls-summary)
  - [6. Push to a container registry (optional)](#6-push-to-a-container-registry-optional)
  - [7. Docker image scan with Trivy (optional)](#7-docker-image-scan-with-trivy-optional)
- [Running Tests](#running-tests)
- [Troubleshooting](#troubleshooting)

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
| `MINIO_ENDPOINT` | Settlement service (`minio` profile) | MinIO / S3 endpoint URL (e.g. `http://localhost:9000`) |
| `MINIO_ACCESS_KEY` | Settlement service (`minio` profile) | MinIO access key (default dev: `minioadmin`) |
| `MINIO_SECRET_KEY` | Settlement service (`minio` profile) | MinIO secret key (default dev: `minioadmin`) |
| `MINIO_BUCKET` | Settlement service (`minio` profile) | Bucket name for invoice files (default: `invoice-files`) |

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

# To check Databases
docker exec -it postgres-ddd-clean psql -U root -l
# To check Tables into database
docker exec -it postgres-ddd-clean psql -U root -d healthcare_db -c "\dt"
docker exec -it postgres-ddd-clean psql -U root -d medicalsalesrep_db -c "\dt"
# To check records into table
docker exec -it postgres-ddd-clean psql -U root -d healthcare_db -c "SELECT * FROM health_care_profs LIMIT 10;"
docker exec -it postgres-ddd-clean psql -U root -d medicalsalesrep_db -c "SELECT * FROM medical_sales_reps LIMIT 10;"


```

**Microsoft SQL Server** (Visit Service):

```bash
docker run -u 0 \
  -e 'ACCEPT_EULA=Y' \
  -e 'MSSQL_SA_PASSWORD=Riverplate1!' \
  -p 1433:1433 \
  --name sqlserver-ddd-clean \
  -v sqlvolume:/var/opt/mssql/data \
  -d mcr.microsoft.com/mssql/server:2022-latest

# Wait for SQL Server to be ready (can take 30-60 s; loop retries every 5 s)
# (-No suppresses the TLS certificate warning on the 2022 image)
until docker exec sqlserver-ddd-clean /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "Riverplate1!" -No -Q "SELECT 1" &>/dev/null; do
  echo "Waiting for SQL Server to be ready..."; sleep 5
done

# Create the database
docker exec sqlserver-ddd-clean /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "Riverplate1!" -No \
  -Q "CREATE DATABASE visitdb"

docker exec sqlserver-ddd-clean /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "Riverplate1!" -No \
  -Q "SELECT * FROM visitdb.dbo.visits"

# To restart later
docker start sqlserver-ddd-clean
```

**MySQL** (Settlement Service only):

```bash
docker run -d --name mysql-ddd-clean \
  -p 3308:3306 \
  -e MYSQL_ROOT_PASSWORD=riverplate \
  -e MYSQL_DATABASE=settlementdb \
  -v mysql_data:/var/lib/mysql \
  mysql:latest

# To restart later
docker start mysql-ddd-clean

# To check databases
docker exec -it mysql-ddd-clean mysql -u root -p -e "SHOW DATABASES;"
# To check tables in database
docker exec -it mysql-ddd-clean mysql -u root -p -e "SHOW TABLES FROM settlementdb;"


```

**RabbitMQ**:

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management



# Management UI: http://localhost:15672  (guest / guest)
```

**MinIO** (Settlement Service — S3-compatible invoice file storage, optional):

> Required only when you activate the `minio` Spring profile. Without it the Settlement Service uses the built-in local-disk adapter and writes files to `INVOICE_FILE_STORAGE_PATH` on the host — no extra container needed.

```bash
# 1. Start MinIO
docker run -d \
  --name minio-ddd-clean \
  -p 9000:9000 \
  -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -v minio-data:/data \
  --restart unless-stopped \
  minio/minio server /data --console-address ":9001"

# 2. Create the invoice-files bucket (run after MinIO is healthy)
docker run --rm --network host minio/mc mc alias set local http://localhost:9000 minioadmin minioadmin && \
docker run --rm --network host minio/mc mc mb --ignore-existing local/invoice-files && \
docker run --rm --network host minio/mc mc anonymous set none local/invoice-files && \
echo 'Bucket invoice-files ready.'

# To restart later
docker start minio-ddd-clean

# Management console: http://localhost:9001  (minioadmin / minioadmin)
# S3 API:            http://localhost:9000
```

> **Dev container note:** if running inside a VS Code Dev Container, connect `minio-ddd-clean` to the shared network (see [Troubleshooting](#dev-container-connection-refused-to-database-containers)):
> ```bash
> docker network connect ddd-clean-net minio-ddd-clean
> ```
> Then set `MINIO_ENDPOINT=http://minio-ddd-clean:9000` when starting the Settlement Service.

#### Verify it's running

```bash
# MinIO web console (browser)
open http://localhost:9001        # user: minioadmin / minioadmin

# List buckets via mc client
docker run --rm --network host minio/mc mc alias set local http://localhost:9000 minioadmin minioadmin && \
docker run --rm --network host minio/mc mc ls local
```

#### To activate `MinioInvoiceFileStorage` in the Spring Boot app instead of local disk

Set the `minio` Spring profile alongside the existing profile when starting the Settlement Service:

```bash
SPRING_PROFILES_ACTIVE=dev,minio \
MINIO_ENDPOINT=http://localhost:9000 \
MINIO_ACCESS_KEY=minioadmin \
MINIO_SECRET_KEY=minioadmin \
MINIO_BUCKET=invoice-files \
mvn -pl settlement-service/settlement-application -am spring-boot:run

SPRING_PROFILES_ACTIVE=dev,minio \
MINIO_ENDPOINT=http://172.17.0.1:9000 \
MINIO_ACCESS_KEY=minioadmin \
MINIO_SECRET_KEY=minioadmin \
MINIO_BUCKET=invoice-files \
java -jar settlement-service/settlement-application/target/settlement-application-*.jar
```

Without `minio` in the active profiles, `LocalDiskInvoiceFileStorage` is used and MinIO is not needed.

### 1b. Create databases (if they don't exist)

If your containers are already running but the databases were never created, use the commands below. Each block is idempotent — safe to run even if the database already exists.

**PostgreSQL — `healthcare_db` and `medicalsalesrep_db`**

```bash
# Create healthcare_db (skip if it already exists)
docker exec -it postgres-ddd-clean \
  psql -U root -d postgres \
  -c "SELECT 1 FROM pg_database WHERE datname='healthcare_db'" | grep -q 1 \
  || docker exec -it postgres-ddd-clean psql -U root -d postgres \
     -c "CREATE DATABASE healthcare_db;"

# Create medicalsalesrep_db (skip if it already exists)
docker exec -it postgres-ddd-clean \
  psql -U root -d postgres \
  -c "SELECT 1 FROM pg_database WHERE datname='medicalsalesrep_db'" | grep -q 1 \
  || docker exec -it postgres-ddd-clean psql -U root -d postgres \
     -c "CREATE DATABASE medicalsalesrep_db;"

# Verify
docker exec -it postgres-ddd-clean psql -U root -l
```

**SQL Server — `visitdb`**

```bash
# Create visitdb if it doesn't exist
docker exec sqlserver_ddd_clean /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "Riverplate1!" -No \
  -Q "IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = 'visitdb') CREATE DATABASE visitdb"

# Verify
docker exec sqlserver_ddd_clean /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "Riverplate1!" -No \
  -Q "SELECT name FROM sys.databases WHERE name = 'visitdb'"
```

**MySQL — `settlementdb`**

```bash
# Create settlementdb if it doesn't exist
docker exec mysql-ddd-clean mysql -u root -p"yourpassword" \
  -e "CREATE DATABASE IF NOT EXISTS settlementdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# Verify
docker exec mysql-ddd-clean mysql -u root -p"yourpassword" \
  -e "SHOW DATABASES LIKE 'settlementdb';"
```

> **Tip:** Replace `yourpassword` with the password you set in `MYSQL_ROOT_PASSWORD` when you created the MySQL container.

** Start SQL Server if not already running **
docker start sqlserver_ddd_clean

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

**Terminal 4 — Medical Sales Rep Service**

```bash
mvn -pl medical-sales-rep-service/msr-application -am spring-boot:run
# Ready when: "Started MsrApplication" appears
# Listens on: http://localhost:8086
```

**Terminal 5 — Healthcare Prof Service**

```bash
mvn -pl healthcare-prof-service/hcp-application -am spring-boot:run
# Ready when: "Started HcpApplication" appears
# Listens on: http://localhost:8087
```

**Terminal 6 — Visit Service**

> **Preparation:** Ensure the SQL Server container is running and `visitdb` exists (see step 1).
> The `DB_PASSWORD` environment variable passes the SQL Server SA password.

```bash
# Run the Visit Service
DB_PASSWORD='Riverplate1!' mvn -pl visit-service/visit-application -am spring-boot:run --no-transfer-progress
# Ready when: "Started VisitApplication" appears
# Listens on: http://localhost:8088
```

**Terminal 7 — Settlement Service**

```bash
# Default — local-disk file storage (no extra container needed)
mvn -pl settlement-service/settlement-application -am spring-boot:run


DB_URL='jdbc:mysql://172.17.0.1:3308/settlementdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true' \
DB_PASSWORD='riverplate' \
mvn -pl settlement-service/settlement-application -am spring-boot:run

# Ready when: "Started SettlementApplication" appears
# Listens on: http://localhost:8089
```

> **Optional — activate MinIO file storage** (requires `minio-ddd-clean` container running, see step 1):
> ```bash
> SPRING_PROFILES_ACTIVE=dev,minio \
> MINIO_ENDPOINT=http://localhost:9000 \
> MINIO_ACCESS_KEY=minioadmin \
> MINIO_SECRET_KEY=minioadmin \
> MINIO_BUCKET=invoice-files \
> mvn -pl settlement-service/settlement-application -am spring-boot:run
> ```

All commands run from the repo root.

> **Shortcut — start everything with one command**
>
> Instead of opening seven terminals, you can use the provided shell script which handles ordering and health-checks automatically:
>
> ```bash
> # Make executable (first time only)
> chmod +x start-all-services.sh
>
> # Run from the repo root
> ./start-all-services.sh
> ```
>
> The script starts Eureka Server first, waits for it to become healthy, then starts the API Gateway, waits for it, and finally launches all remaining microservices in parallel. Per-service output is written to `logs/<service-name>.log`.
>
> Alternatively, use the **VS Code task**: open the Command Palette (`Ctrl+Shift+P`) → **Tasks: Run Task** → **Run All Microservices (script)**.

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

## Option C — Run Individual Docker Containers

Use this option to build and run each service as a standalone Docker container on a shared network.
All services must be on the same Docker network (`ddd-clean-net`) and use `SPRING_PROFILES_ACTIVE=prod`
so that `server.address=0.0.0.0` and Eureka IP auto-detection are activated.

### 0. Prerequisites — shared network and dependency containers

```bash
# Create the shared network (once)
docker network create ddd-clean-net

# Connect existing dependency containers to ddd-clean-net
docker network connect ddd-clean-net postgres-ddd-clean   # MSR + HCP (PostgreSQL)
docker network connect ddd-clean-net sqlserver-ddd-clean  # Visit (SQL Server)
docker network connect ddd-clean-net mysql-ddd-clean      # Settlement (MySQL)
docker network connect ddd-clean-net rabbitmq-ddd-clean   # RabbitMQ (or rabbitmq-ddd-clean)
```

> If a container is already on `ddd-clean-net` the command returns an error you can safely ignore.

---

Each service's build command is also captured in a `<service>/build.sh` script —
`./eureka-server/build.sh`, `./visit-service/build.sh`, etc. — so the correct
context and `--build-context` flags never have to be reconstructed by hand.
Every Dockerfile also carries a header comment stating its own required
build context. The commands below are what those scripts run.

> **Build context matters.** Every Dockerfile does `COPY . .` expecting the
> service's *own* directory as the build context — not the repo root. Passing
> `.` instead sends the whole monorepo in, and the build fails, either at
> `COPY --from=<name>` (Docker tries to pull `<name>` as a registry image
> instead of using the named context you meant) or later at `mvn -pl
> <module>` (`Could not find the selected project in the reactor` — the
> module lives two directories deeper than Maven is looking).

### 1. Eureka Server

```bash
# Build
./eureka-server/build.sh
# equivalent to:
docker build -f eureka-server/Dockerfile -t eureka-server:local eureka-server

# Run
docker run -d --name eureka-server --network ddd-clean-net -p 8761:8761 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e EUREKA_USER=eureka \
  -e EUREKA_PASSWORD=eureka \
  -e EUREKA_HOSTNAME=eureka-server \
  eureka-server:local
```

Dashboard: `http://localhost:8761` (eureka / eureka)

---

### 2. API Gateway

```bash
# Build
./api-gateway/build.sh
# equivalent to:
docker build -f api-gateway/Dockerfile -t api-gateway:local api-gateway

# Run
docker run -d --name api-gateway --network ddd-clean-net -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET=your-secret-32-chars-minimum \
  -e EUREKA_URL=http://eureka:eureka@eureka-server:8761/eureka/ \
  -e CORS_ALLOWED_ORIGINS=http://localhost:5173 \
  api-gateway:local
```

---

### 3. Identity Service

```bash
# Build
./identity-service/build.sh
# equivalent to:
docker build -f identity-service/identity-application/Dockerfile \
  -t identity-service:local identity-service

# Run
docker run -d --name identity-service --network ddd-clean-net -p 8090:8090 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET=your-secret-32-chars-minimum \
  -e EUREKA_URL=http://eureka:eureka@eureka-server:8761/eureka/ \
  identity-service:local
```

Auth endpoint: `POST http://localhost:8090/auth/login`

---

### 4. Medical Sales Rep Service

```bash
# Build
./medical-sales-rep-service/build.sh
# equivalent to:
DOCKER_BUILDKIT=1 docker build \
  --build-context domain-commons=domain-commons \
  -f medical-sales-rep-service/msr-application/Dockerfile \
  -t medical-sales-rep-service:local \
  medical-sales-rep-service

# Run
docker run -d --name msr-service --network ddd-clean-net -p 8086:8086 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET=your-secret-32-chars-minimum \
  -e EUREKA_URL=http://eureka:eureka@eureka-server:8761/eureka/ \
  -e DB_URL=jdbc:postgresql://postgres-ddd-clean:5432/medicalsalesrep_db \
  -e PG_USERNAME=root \
  -e PG_PASSWORD=river \
  -e RABBITMQ_HOST=rabbitmq \
  -e RABBITMQ_USERNAME=guest \
  -e RABBITMQ_PASSWORD=guest \
  medical-sales-rep-service:local
```

---

### 5. Healthcare Prof Service

```bash
# Build
./healthcare-prof-service/build.sh
# equivalent to:
DOCKER_BUILDKIT=1 docker build \
  --build-context domain-commons=domain-commons \
  -f healthcare-prof-service/hcp-application/Dockerfile \
  -t healthcare-prof-service:local \
  healthcare-prof-service

# Run
docker run -d --name hcp-service --network ddd-clean-net -p 8087:8087 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET=your-secret-32-chars-minimum \
  -e EUREKA_URL=http://eureka:eureka@eureka-server:8761/eureka/ \
  -e DB_URL=jdbc:postgresql://postgres-ddd-clean:5432/healthcare_db \
  -e PG_USERNAME=root \
  -e PG_PASSWORD=river \
  -e RABBITMQ_HOST=rabbitmq \
  -e RABBITMQ_USERNAME=guest \
  -e RABBITMQ_PASSWORD=guest \
  healthcare-prof-service:local
```

---

### 6. Visit Service

> **Requires:** `sqlserver-ddd-clean` container on `ddd-clean-net` with `visitdb` created.

```bash
# Build
./visit-service/build.sh
# equivalent to:
DOCKER_BUILDKIT=1 docker build \
  --build-context domain-commons=domain-commons \
  --build-context medical-sales-rep-service=medical-sales-rep-service \
  --build-context healthcare-prof-service=healthcare-prof-service \
  -f visit-service/visit-application/Dockerfile \
  -t visit-service:local \
  visit-service

# Run
docker run -d --name visit-service --network ddd-clean-net -p 8088:8088 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET=your-secret-32-chars-minimum \
  -e EUREKA_URL=http://eureka:eureka@eureka-server:8761/eureka/ \
  -e DB_URL="jdbc:sqlserver://sqlserver-ddd-clean:1433;databaseName=visitdb;encrypt=false;trustServerCertificate=true" \
  -e DB_USERNAME=sa \
  -e DB_PASSWORD=Riverplate1! \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
  -e RABBITMQ_HOST=rabbitmq \
  -e RABBITMQ_USERNAME=guest \
  -e RABBITMQ_PASSWORD=guest \
  visit-service:local
```

> `SPRING_JPA_HIBERNATE_DDL_AUTO=update` creates tables on first run. Remove it on subsequent runs.
>
> visit-service reaches MSR and HCP through Eureka client-side load balancing
> (their base URLs are hardcoded to the Eureka service ids
> `medical-sales-rep-service` / `healthcare-prof-service`, resolved by the
> `@LoadBalanced` `RestTemplate` — not by container DNS), so it only needs
> `EUREKA_URL` here, not `MSR_BASE_URL` / `HCP_BASE_URL`. Both services must
> be registered and healthy in Eureka before visit-service starts handling
> requests that call them.

---

### 7. Settlement Service

> **Requires:** `mysql-ddd-clean` container on `ddd-clean-net` with `settlementdb` created.

```bash
# Build
./settlement-service/build.sh
# equivalent to:
DOCKER_BUILDKIT=1 docker build \
  --build-context domain-commons=domain-commons \
  -f settlement-service/settlement-application/Dockerfile \
  -t settlement-service:local \
  settlement-service

# Run
docker run -d --name settlement-service --network ddd-clean-net -p 8089:8089 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e JWT_SECRET=your-secret-32-chars-minimum \
  -e EUREKA_URL=http://eureka:eureka@eureka-server:8761/eureka/ \
  -e DB_URL="jdbc:mysql://mysql-ddd-clean:3306/settlementdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  -e DB_USERNAME=root \
  -e DB_PASSWORD=yourpassword \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
  -e MSR_SERVICE_BASE_URL=http://medical-sales-rep-service \
  -e INVOICE_FILE_STORAGE_PATH=/var/settlement-service/invoice-files \
  -v settlement-invoice-files:/var/settlement-service/invoice-files \
  settlement-service:local
```

> `SPRING_JPA_HIBERNATE_DDL_AUTO=update` creates tables on first run. Remove it on subsequent runs.
>
> Unlike MSR/HCP's hardcoded lookup, settlement-service's `msr.service.base-url`
> **is** read from `MSR_SERVICE_BASE_URL` (see `SettlementController`'s
> dependency on `MedicalSalesRepHttpAdapter`) and is also `@LoadBalanced`, so
> the value must be the Eureka service id — `http://medical-sales-rep-service`
> — not a container hostname.

---

### Start-up order

Always start in this order: **Eureka → API Gateway → Identity → MSR → HCP → Visit → Settlement**

### Check logs

```bash
docker logs -f <container-name>
# e.g.
docker logs -f visit-service
```

### Rebuild a single service after code changes

```bash
./<service>/build.sh          # e.g. ./visit-service/build.sh
docker rm -f <container-name>
docker run -d ... <service>:local   # reuse the run command above
```

---

## Option B — Run with Docker Compose (Production)

All services run as Docker containers on an internal network. Only the API Gateway port is published to the host.

### 1. Fill in secrets

```bash
cp .env.example .env
# Edit .env — replace every "change-me" value with a real secret
```

### 2. Create the external Docker network

Several services (`msr`, `hcp`, `visit`) join a pre-existing network called `ddd-clean-net` to reach a RabbitMQ container that lives outside of Compose. Create it once on the Docker host:

```bash
docker network create ddd-clean-net
```

> If this network already exists (e.g. created by another Compose project) the command returns an error you can safely ignore.

### 3. Build and start

```bash
docker compose up -d --build
```

The `--build` flag compiles every service JAR inside Docker (multi-stage build) — required on first run and after any code change.

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
>
> Services with `depends_on: service_healthy` wait for Eureka to pass its healthcheck before starting, so the stack comes up in the correct order automatically.

### 4. Check status

```bash
# Show running containers and their state
docker compose ps

# Follow logs for all services
docker compose logs -f

# Follow logs for a single service
docker compose logs -f api-gateway
docker compose logs -f identity-service
```

### 5. Verify all services registered with Eureka

Eureka is on the internal network and has no published port. Query it through the gateway:

```bash
curl -s -u ${EUREKA_USER}:${EUREKA_PASSWORD} \
  http://localhost:8080/eureka/apps | grep '<app>'
```

Or exec into any container:

```bash
docker compose exec api-gateway \
  curl -s http://eureka-server:8761/actuator/health
```

### 6. Obtain a JWT token

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"Apatehia65$"}'
```

### 7. Dev override — MinIO file storage (optional)

To activate MinIO instead of local-disk storage for the Settlement Service, merge the dev override file:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d --build
```

### 8. Stop

```bash
# Stop and remove containers (keeps volumes)
docker compose down

# Stop, remove containers AND volumes (destructive — deletes invoice-files volume)
docker compose down -v
```

### 9. Rebuild a single service

After changing code in one service only:

```bash
docker compose up -d --build <service-name>
# e.g.
docker compose up -d --build settlement-service
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

## Swagger UI

Each microservice exposes an interactive Swagger UI (powered by **springdoc-openapi**) when running in `dev` mode. Use it to explore endpoints, read request/response schemas, and execute calls directly from the browser.

> **Dev only:** Swagger UI is disabled when `SPRING_PROFILES_ACTIVE=prod` (Docker Compose / production deployments).

### Direct access (per service)

| Service | Swagger UI | OpenAPI JSON |
|---|---|---|
| Identity Service | http://localhost:8090/swagger-ui/index.html | http://localhost:8090/v3/api-docs |
| Medical Sales Rep | http://localhost:8086/swagger-ui/index.html | http://localhost:8086/v3/api-docs |
| Healthcare Prof | http://localhost:8087/swagger-ui/index.html | http://localhost:8087/v3/api-docs |
| Visit | http://localhost:8088/swagger-ui/index.html | http://localhost:8088/v3/api-docs |
| Settlement | http://localhost:8089/swagger-ui/index.html | http://localhost:8089/v3/api-docs |

### How to authenticate in Swagger UI

1. Obtain a JWT token from the Identity Service (see [Authentication](#authentication--identity-service-auth) above).
2. Open the Swagger UI of the target service.
3. Click **Authorize** (lock icon, top right).
4. Enter `Bearer <your-token>` in the **bearerAuth** field and click **Authorize**.
5. All subsequent requests from the UI will include the token automatically.

### API Gateway proxy

The API Gateway does **not** proxy Swagger UI paths. Access each service's Swagger UI directly on its own port as shown in the table above.

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

## CI with Jenkins

Each microservice has a `Jenkinsfile` at its service root. The four DDD services (domain/infra/application split) define a **10-stage declarative pipeline** aligned with DDD Clean Architecture layers and OWASP Top 10 security controls; the single-module infrastructure services (API Gateway, Eureka Server) use a shorter 8-stage variant without the per-layer test split.

| Service | Jenkinsfile path |
|---|---|
| Medical Sales Rep | `medical-sales-rep-service/Jenkinsfile` |
| Healthcare Prof | `healthcare-prof-service/Jenkinsfile` |
| Visit | `visit-service/Jenkinsfile` |
| Settlement | `settlement-service/Jenkinsfile` |
| Identity | `identity-service/Jenkinsfile` |
| API Gateway | `api-gateway/Jenkinsfile` |
| Eureka Server | `eureka-server/Jenkinsfile` |

### 1. Install required plugins

In Jenkins: **Manage Jenkins → Plugins → Available plugins**

| Plugin | Purpose |
|---|---|
| **Pipeline** | Declarative pipeline support (usually pre-installed) |
| **Git** | SCM checkout |
| **JUnit** | Publish per-layer test results |
| **Warnings Next Generation** | `recordIssues` — publishes SpotBugs SAST reports |
| **OWASP Dependency-Check** | `dependencyCheckPublisher` — publishes CVE scan reports |
| **Docker Pipeline** | Docker build/push steps |

### 2. Configure tools

In Jenkins: **Manage Jenkins → Tools**

Add entries with these exact names (they match the `tools {}` block in each Jenkinsfile):

| Tool type | Name | Version |
|---|---|---|
| JDK | `JDK-25` | Java 25 |
| Maven | `Maven-3.9` | 3.9.x |

> If Java 25 and Maven 3.9 are already on the agent's `PATH`, you can remove the `tools {}` block from a Jenkinsfile and they will be used automatically.

### 3. Create a Pipeline job for each service

Repeat these steps for each service:

1. **New Item** → enter the service name → select **Pipeline** → **OK**
2. Under the **Pipeline** section:
   - Definition: **Pipeline script from SCM**
   - SCM: **Git**
   - Repository URL: `https://github.com/diegoarielsanchez/vscode-remote-java-ddd-clean-architecture`
   - Branch: `*/main` (or your target branch)
   - Script Path: see table above
3. **Save** → **Build Now**

### 4. Pipeline stages

```
Checkout → Domain Tests → Infra Tests → Application Tests
         → SAST (SpotBugs) → OWASP Dependency-Check → Package
         → Build Docker Image → [Docker Image Scan*] → [Push*]
```

Each stage maps to a specific DDD layer and OWASP control:

| Stage | DDD layer | OWASP Top 10 control |
|---|---|---|
| **Domain Tests** | `*-domain` | A04 — entity invariants, use-case business rules, SHA-256 integrity (Settlement) |
| **Infra Tests** | `*-infra` | — JPA adapters + file storage adapters tested in isolation with H2 |
| **Application Tests** | `*-application` | A01 Broken Access Control, A07 Auth Failures — security filters, JWT, Bean Validation |
| **SAST — SpotBugs** | all layers | A03 Injection, path traversal, insecure deserialization (Find Security Bugs plugin) |
| **OWASP Dependency-Check** | all layers | A06 Vulnerable & Outdated Components — fails build on CVSS ≥ 7 |
| **Build Docker Image** | — | Multi-stage build, minimal JRE runtime image |
| **Docker Image Scan (Trivy)** | — | A06 — OS-level CVEs in the final image *(commented out, optional)* |

**Stage details:**

- **Domain Tests** — pure JUnit 5 + Mockito, zero Spring context. Business rule regressions are caught before any infrastructure or HTTP test runs.
- **Infra Tests** — validates JPA mappings and repository queries against H2 in-memory; no live database required in CI.
- **Application Tests** — Spring Boot test slice (`@WebMvcTest` / `@SpringBootTest`): verifies security filter chain, JWT token validation, rate limiting, and input validation.
- **SAST — SpotBugs + Find Security Bugs** (`findsecbugs-plugin:1.13.0`) — static analysis at compile time. Detects SQL injection, command injection, XSS, path traversal, and insecure cryptography patterns. Build fails on any finding (`-Dspotbugs.failOnError=true`).
- **OWASP Dependency-Check** — queries the NVD CVE database against every declared Maven dependency. Build fails if any dependency has a CVSS score ≥ 7 (`-DfailBuildOnCVSS=7`). HTML + XML reports are archived and published via the Jenkins plugin.
- **Package** — assembles the final JAR (`-DskipTests`) only after all security gates pass.
- **Build Docker Image** — uses `context: .` (repo root) so the multi-stage Dockerfile can resolve cross-module dependencies.

### 5. OWASP controls summary

| OWASP Top 10 | Pipeline enforcement |
|---|---|
| A01 Broken Access Control | Spring Security integration tests in Application Tests stage |
| A03 Injection | SpotBugs + Find Security Bugs SAST (fails build) |
| A04 Insecure Design | Domain Tests validate business invariants and SHA-256 integrity |
| A05 Security Misconfiguration | Prod profile disables Swagger, enforces `server.address=0.0.0.0` only in Docker |
| A06 Vulnerable Components | OWASP Dependency-Check (fails on CVSS ≥ 7) + optional Trivy image scan |
| A07 Identification & Auth Failures | JWT filter + BCrypt wiring covered by Application Tests |
| A09 Security Logging Failures | Log configuration assertions in application integration tests |

### 6. Push to a container registry (optional)

Each Jenkinsfile contains a commented-out `Push Docker Image` stage. To activate it:

1. Add a Jenkins credential (**Manage Jenkins → Credentials**) of type **Username with password**, ID: `docker-registry-credentials`.
2. Uncomment the `Push Docker Image` stage in the Jenkinsfile.
3. Replace `<your-registry>` with your registry URL (e.g. `docker.io/myorg`, `123456789.dkr.ecr.us-east-1.amazonaws.com`, `ghcr.io/myorg`).

### 7. Docker image scan with Trivy (optional)

Each Jenkinsfile also contains a commented-out `Docker Image Scan — Trivy` stage. To activate it:

1. Install Trivy on the Jenkins agent:
   ```bash
   curl -sfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh | sh -s -- -b /usr/local/bin
   ```
2. Uncomment the `Docker Image Scan — Trivy` stage in the Jenkinsfile.
3. The stage fails the build on any `HIGH` or `CRITICAL` OS/library CVE in the final image.

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

## Troubleshooting

### Dev Container: "Connection refused" to database containers

When running inside a **VS Code Dev Container**, the Spring Boot services run inside a container themselves. Docker assigns dynamic IPs to sibling containers (SQL Server, MySQL, PostgreSQL) which change on every restart, so hardcoded IPs like `172.17.0.x` stop working.

#### Permanent fix — user-defined Docker network

Create a shared network once and connect all relevant containers to it. Docker then resolves container names as hostnames, no IPs needed.

**Step 1 — Create the network**

```bash
docker network create ddd-clean-net
```

**Step 2 — Connect the database containers**

```bash
docker network connect ddd-clean-net sqlserver_ddd_clean
docker network connect ddd-clean-net mysql-ddd-clean
docker network connect ddd-clean-net postgres-ddd-clean   # if running
docker network connect ddd-clean-net minio-ddd-clean      # if using MinIO profile
```

**Step 3 — Find and connect the dev container**

```bash
# Find your dev container name
docker ps --format '{{.Names}}' | grep -i vscode

# Connect it to the shared network
docker network connect ddd-clean-net <your-devcontainer-name>
```

**Step 4 — Use container names in `application.properties`**

Update each service's `spring.datasource.url` default to use the container name instead of an IP:

| Service | Updated URL |
|---|---|
| Visit Service | `jdbc:sqlserver://sqlserver_ddd_clean:1433;databaseName=visitdb;encrypt=false;trustServerCertificate=true` |
| Settlement Service | `jdbc:mysql://mysql-ddd-clean:3306/settlementdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true` |
| MSR / HCP Services | `jdbc:postgresql://postgres-ddd-clean:5432/medicalsalesrep_db` / `healthcare_db` |

> The `docker network connect` commands survive container restarts — you only need to run them once per network. If you recreate a container (not just restart it), re-run the `connect` command for that container.

#### Quick workaround — pass `DB_URL` explicitly at startup

If you don't want to change `application.properties`, override the URL at runtime:

```bash
# Visit Service — find the current SQL Server IP first
docker inspect sqlserver_ddd_clean --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'

DB_URL='jdbc:sqlserver://<ip>:1433;databaseName=visitdb;encrypt=false;trustServerCertificate=true' \
DB_PASSWORD='Riverplate1!' \
mvn -pl visit-service/visit-application -am spring-boot:run --no-transfer-progress

# 1. Create shared network (once)
docker network create ddd-clean-net

# 2. Connect SQL Server (and other DB containers) to it
docker network connect ddd-clean-net sqlserver_ddd_clean

# 3. Find your dev container name and connect it
docker ps --format '{{.Names}}' | grep -i vscode
docker network connect ddd-clean-net <your-devcontainer-name>


# Settlement Service — find the current MySQL IP first
docker inspect mysql-ddd-clean --format '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}'

DB_URL='jdbc:mysql://<ip>:3306/settlementdb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true' \
mvn -pl settlement-service/settlement-application -am spring-boot:run --no-transfer-progress
```

---

## License

Licensed under the MIT License. See [LICENSE](LICENSE) for details.

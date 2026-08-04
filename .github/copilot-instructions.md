# Copilot instructions for this repository

This file explains the minimal, actionable knowledge an AI coding agent needs to be productive in this multi-module Spring Boot project.

**Big Picture**
- **Repository layout**: root is a parent Maven POM with three modules: `application`, `domain`, `library` (see `pom.xml`).
- **Responsibilities**: `domain` contains DDD entities and use-cases; `library` holds concrete service/repository implementations (e.g. in-memory repository); `application` is the Spring Boot web layer that composes use-cases and exposes HTTP endpoints.
- **Bootstrapping**: main class is `application/src/main/java/com/das/cleanddd/application/DemoApplication.java`. It uses `@SpringBootApplication(scanBasePackages=...)` — DI scanning is explicit across `com.das.cleanddd.*` packages.

**Key files & examples**
- Main startup: `application/src/main/java/com/das/cleanddd/application/DemoApplication.java` (scanBasePackages configuration).
- HTTP API example: `application/src/main/java/com/das/cleanddd/application/MedicalSalesRepController.java` — endpoints under `/api/v1/medicalsalesrep` (create, update, activate, deactivate, get, list).
- Domain use-cases: `domain/src/main/java/com/das/cleanddd/domain/medicalsalesrep/usecases/services/MedicalSalesRepUseCaseFactory.java` (factory wiring use-cases to repositories).
- Repository implementation: `library/src/main/java/com/das/cleanddd/service/medicalsalesrep/InMemoryMedicalSalesRepRepository.java` — indicates there is currently no external DB; implementations are Spring `@Service` beans.
- Global error handling: `application/src/main/java/com/das/cleanddd/application/GlobalExceptionHandler.java` (maps domain exceptions to HTTP responses).

**Build / run / test workflows**
- Java target: project uses Java 25 (`<java.version>21</java.version>` in POMs). Ensure JDK 25 is used when building or running.
- Build all modules: `mvn clean install` (run at repository root).
- Run the application module locally: from repo root run:
  - `mvn -pl application -am spring-boot:run` (builds required modules and starts the Spring Boot app)
  - Or `mvn -pl application -am package` then `java -jar application/target/*.jar`.
- Run tests for a module: `mvn -pl domain test` (module-level invocation); CI uses Maven/Surefire reports visible under `*/target/surefire-reports`.

**Conventions & patterns to follow**
- DDD separation: keep domain logic in `domain`, avoid adding business rules to controllers. Use `UseCase` interfaces and DTOs in `domain/.../usecases/dtos`.
- Wiring pattern: controllers obtain use-cases from a factory class (e.g. `MedicalSalesRepUseCaseFactory`) rather than new-ing use-cases directly.
- Repository beans live in `library` as Spring `@Service` implementations and are injected into domain factories. Typical repository class: `InMemoryMedicalSalesRepRepository`.
- Error handling: domain throws `DomainException` and controllers convert to `ResponseStatusException` or rely on `GlobalExceptionHandler`.

**Integration points & external deps**
- No external database is present by default — the project uses an in-memory repository implementation under `library`. If adding persistence, register the new repository as a Spring bean and keep the `MedicalSalesRepRepository` interface contract.
- Tests use Mockito and `javafaker` (see `domain/pom.xml`). Be consistent with existing test styles (unit tests mock `MedicalSalesRepRepository`).

**Project quirks & gotchas**
- Parent POM versions vary between modules (see `application/pom.xml`, `domain/pom.xml`, `library/pom.xml`). Be conservative when changing parent Spring Boot versions — check module compatibility.
- The `DemoApplication` class uses explicit `scanBasePackages`; adding new top-level packages may require updating the scan list.
- Several use-cases and DTOs follow simple naming conventions: `*InputDTO`, `*OutputDTO`, `*IDDto`. Use those when adding new endpoints or tests.

**How to approach common tasks (examples)**
- Add a new API endpoint that needs domain logic: add DTOs and use-case class under `domain/.../usecases`, update `MedicalSalesRepUseCaseFactory` (or create a new factory service) and expose it from the controller.
- Replace in-memory repo with DB-backed repo: implement `MedicalSalesRepRepository` in `library` (or new module), annotate as `@Service`/`@Repository`, ensure component scan finds it and run `mvn -pl application -am spring-boot:run` to test.

If anything here is unclear or you want the file to include extra examples (tests, CI commands, or contributor instructions), tell me which section to expand and I will iterate.

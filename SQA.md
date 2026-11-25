# Software Quality Assurance (SQA)

How quality is built in and checked at each stage of the software development lifecycle (SDLC) of this project, what
actually blocks a change today, and the known gaps. It is written for developers changing the services and for
reviewers, Quality and validation people who need to know what the evidence is worth.

- [Scope](#scope)
- [Quality across the SDLC](#quality-across-the-sdlc)
- [Test strategy](#test-strategy)
- [Quality gates](#quality-gates)
- [Design quality: SOLID and clean architecture](#design-quality-solid-and-clean-architecture)
- [Change and defect management](#change-and-defect-management)
- [Definition of Done](#definition-of-done)
- [Gaps and improvement backlog](#gaps-and-improvement-backlog)

Related: [SECURITY.md](SECURITY.md) (OWASP findings and fixes) · [PRODUCTS.md](PRODUCTS.md) (GxP requirements traced
to tests, GAMP 5 view) · [README, CI with Jenkins](README.md#ci-with-jenkins) ·
[README, Running Tests](README.md#running-tests)

## Scope

| In scope | Not in scope |
|---|---|
| The Java microservices, `domain-commons`, the API Gateway and Eureka | Infrastructure qualification (servers, databases, brokers) |
| The React PWA (`pwa/`) | Formal validation deliverables (plan, IQ/OQ/PQ, report): see [PRODUCTS.md](PRODUCTS.md#gamp-5-view-of-the-system) |
| CI pipelines (GitHub Actions, Jenkins) and their gates | The Android app (`mobile-android/`), which has its own Gradle build |
| Performance and resilience tests (`load-test/`) | |

## Quality across the SDLC

| SDLC stage | Quality activity | Evidence in the repository | Status |
|---|---|---|---|
| **Requirements** | Requirements written with an id and traced to tests | [PRODUCTS.md](PRODUCTS.md) (`PRD-…` ids) for product-catalog-service and order-service | Done for products and orders only. Other services have no traced requirements |
| | Security requirements | [SECURITY.md](SECURITY.md), OWASP Top 10 categories | Done |
| **Design** | Layered clean architecture, one bounded context per service | `*-domain` / `*-infra` / `*-application` modules; ports in the domain, adapters in infra | Done, with the deviations under [Design quality](#design-quality-solid-and-clean-architecture) |
| | Architecture decisions recorded | README sections, `docs/diagrams/` | Partial: no decision records (ADRs) |
| **Construction** | Business rules in value objects and aggregates, validated on construction | e.g. `Gtin`, `BatchNumber`, `IssueDate`, `VisitDateTime` | Done |
| | Static analysis (SAST) | SpotBugs + Find Security Bugs, bound to `verify` on the `*-application` modules | Runs in Jenkins only (see [Quality gates](#quality-gates)) |
| | Coding standards | None written down: `.github/copilot-instructions.md` still describes an earlier single-module layout | No formatter or style check enforced in the build |
| **Verification** | Unit, slice, integration, security and contract tests | 163 Java test classes, about 1,000 test methods; 6 PWA test files | Done; coverage not measured |
| | Performance and resilience | k6 scripts in `load-test/` (event flow, outbox relay, Redis cache) | Manual, on a disposable environment |
| **Release** | Reproducible build and container image | Maven wrapper, per-service `Dockerfile`, Jenkins `Build Docker Image` stage | Done |
| | Vulnerable dependencies (OWASP A06) | Jenkins `OWASP Dependency-Check` stage, `owasp-suppressions.xml` | Done: runs in every service pipeline, fails on CVSS ≥ 7 |
| | Image scan | Trivy stage in each `Jenkinsfile` | Commented out |
| **Operation** | Logs, metrics, traces | OpenTelemetry → Loki, Prometheus, Jaeger, Grafana (`observability/`) | Done |
| | Audit trail of business changes | `audit_event`, append-only (PRD-40 to PRD-42) | Done for HCP, MSR, Product Catalog, Order, Visit |
| **Maintenance** | Dependency updates | `.github/dependabot.yml` | Covers the dev container only, not Maven, npm, Docker or Actions |

## Test strategy

Tests follow the layers, so most of them run without Spring, a database or a broker.

| Layer | Kind of test | Tools | What it proves |
|---|---|---|---|
| `*-domain`, `domain-commons` | Unit | JUnit 5, AssertJ, Mockito | Aggregate invariants, value object rules, use case logic. **623 tests, all passing** on 2026-11-12 (`mvn test` on the domain modules) |
| `*-infra` | Persistence slice | `@DataJpaTest` on H2 | Repository mapping, queries, pagination |
| `*-infra` | Integration | Testcontainers (`@Testcontainers`, 8 classes) | Behaviour against the real database or broker |
| `*-infra` | Schema migration | `*SchemaScriptTest` | Each idempotent SQL script upgrades the previous schema and passes Hibernate `validate` |
| `*-infra` | Messaging | `OutboxWiringTest`, `*EventTranslatorTest`, saga tests | Outbox atomicity, event shape, the order stock saga |
| `*-application` | Security | `*ControllerSecurityTest`, `@WebMvcTest`, `@SpringBootTest` | Role rules and ownership checks with real signed JWTs |
| `pwa/` | Unit and smoke | Vitest, Testing Library; Playwright smoke in `pwa/e2e/` | View-model logic; the built app loads under the production security headers |
| `backoffice-ui/` | Unit and smoke | Node test runner; Playwright smoke against a mock gateway in `backoffice-ui/e2e/`, both in `backoffice-ui/Jenkinsfile` | Session, CSP, cache isolation, access control and CSRF in the production build |
| System | Load and failure | k6 (`load-test/`) | Throughput, relay lag, cache behaviour when Redis or RabbitMQ fails |

**Test design rules used in this repository**

- A requirement or a fixed bug gets a test named after the behaviour (`aRejectedLotIsNoLongerOnHand`), not the method.
- Security rules are tested from outside the controller, with a signed token, so a misordered matcher fails the test.
- A database change ships with a schema test that runs it against the previous schema.
- Tests that need Docker are Testcontainers tests; everything else must run offline.

## Quality gates

What blocks a change today, and what only looks like it does.

| Gate | Where | Blocks the change? |
|---|---|---|
| Compile + all tests (`./mvnw package`) | GitHub Actions, every push and pull request | **Yes**, but only if the branch is protected so that `main` requires the check |
| Domain, infra and application tests per service | Jenkins, one pipeline per service | **Yes** for that service's pipeline |
| SpotBugs + Find Security Bugs (`failOnError`) | Jenkins `SAST — SpotBugs` stage (`verify`) | **Yes** in Jenkins. **No** in GitHub Actions, which stops at `package` |
| OWASP Dependency-Check, CVSS ≥ 7 | Jenkins `OWASP Dependency-Check` stage | **Yes**, in all 10 pipelines. Without an `nvd-api-key` credential the first run can take 30 minutes or more |
| Back office: typecheck, unit tests, `npm audit` (HIGH/CRITICAL), browser smoke | Jenkins `backoffice-ui/Jenkinsfile` | **Yes** for that pipeline. Not run by GitHub Actions |
| Trivy image scan, HIGH/CRITICAL | Jenkins | **No**: commented out |
| Code coverage threshold | — | **No**: no coverage tool is configured |
| Architecture rules (layering, no framework in domain) | — | **No**: not enforced by a test |
| Peer review | GitHub pull requests | **No**: most recent changes were merged locally, without a pull request |

## Design quality: SOLID and clean architecture

Reviewed on 2026-11-12 across all services.

**What holds**

- **Single responsibility.** One use case per class (`CreateVisitUseCase`, `UpdateVisitUseCase`, …); rules live in
  small value objects. The largest production class has about 320 lines (`Order`).
- **Open/closed.** Every use case implements `UseCase<I, O>` (or `UseCaseOnlyInput`/`UseCaseOnlyOutput`) from
  `domain-commons`; new behaviour is a new class, not a branch in an existing one. Event publishing and file storage
  are ports with swappable adapters (outbox publisher, no-op publisher for `dev`, file system or MinIO storage).
- **Interface segregation.** Repository ports are small (6–8 methods), and cross-service checks use narrow ports such
  as `IHealthCareProfValidator` and `IMedicalSalesRepValidator`.
- **Dependency inversion.** Domain modules depend only on `domain-commons`. JPA, RabbitMQ, Redis and HTTP clients are
  in `*-infra` behind domain interfaces.

**Deviations found**

| # | Principle | Finding | Where | Suggested fix |
|---|---|---|---|---|
| D1 | LSP, ISP, bounded contexts | visit-infra depends on `msr-domain` and `hcp-domain` and implements their full `IMedicalSalesRepRepository` / `IHealthCareProfRepository`, but `save`, `findByName`, `matching` and others throw `UnsupportedOperationException`. Any caller using the interface as declared fails at runtime, and a change in another service's domain can break visit-service's build. | `HttpMedicalSalesRepRepository`, `HttpHealthCareProfRepository`, `SnapshotBootstrapService` | **Fixed 2026-11-12.** Only the startup snapshot seeding used them, and only to list records. They were replaced by `UpstreamDirectoryClient`, which returns plain records of the upstream JSON (and now reads every page instead of the first 100 rows). visit-service no longer depends on `msr-domain` or `hcp-domain`, nor needs their source trees to build its image |
| D2 | DIP (domain free of frameworks) | Use cases in every `*-domain` module carry Spring's `@Service` and `@Autowired`; `DraftProposalFactory` reads `@Value`; three settlement DTOs use Jackson's `@JsonFormat`. The domain compiles only with Spring on the classpath. | 40 `@Service`, 37 `@Autowired` in `*-domain` | Register use cases as beans in `*-application` configuration, pass configuration values through constructors, and move JSON annotations to request DTOs in `*-application` |
| D3 | DIP, single source of wiring | Use cases are Spring beans **and** are built with `new` inside the `*UseCaseFactory` classes, so two instances of each exist and the factory hides the real dependencies. | `VisitUseCaseFactory` and the other `*UseCaseFactory` classes | Keep one: either inject use cases directly into controllers, or keep the factories and drop `@Service` from the use cases |
| D4 | Clarity | `@Autowired` on `private final` fields that are already set by the constructor (79 places). It does nothing, but suggests field injection. | Most use cases | Remove the annotation; constructor injection needs none |
| D5 | ISP | Controllers depend on a whole `*UseCaseFactory` rather than the use cases they call. | All `*Controller` classes | Follows from D3 |

None of the open ones (D2–D5) is a defect in today's behaviour. They are design debt that tests don't currently
guard against; see G4 in the backlog.

## Change and defect management

- **Branches.** Work is done on a branch named by type (`feat/…`, `fix/…`, `chore/…`) and merged into `main`.
- **Commits.** One logical change per commit, with a message that says why.
- **Defects.** A fixed defect gets a regression test that fails without the fix (see the timing-attack and `GET`-body
  fixes in [SECURITY.md](SECURITY.md)).
- **Security issues** are reported privately: see [SECURITY.md](SECURITY.md#reporting-a-vulnerability).
- **GxP-relevant changes** (product-catalog-service, order-service) follow
  [PRODUCTS.md, Changing these rules](PRODUCTS.md#changing-these-rules).

## Definition of Done

A change is done when:

1. The requirement or defect it addresses is stated in the pull request, and for products and orders its `PRD-…` id
   is updated.
2. New behaviour has tests in the lowest layer that can prove it; a fixed defect has a regression test.
3. `./mvnw verify` passes locally for the affected services, including SpotBugs.
4. Security-relevant changes have a test through the HTTP layer with a real token, and [SECURITY.md](SECURITY.md) is
   updated if a finding was fixed.
5. A database change ships as an idempotent script with a schema test.
6. Published events stay backward compatible (additive fields, same schema version).
7. README, PRODUCTS.md or this document is updated if the behaviour they describe changed.
8. Another person has reviewed it in a pull request.

## Gaps and improvement backlog

In priority order.

| # | Gap | Risk | Next step |
|---|---|---|---|
| G1 | **Dependency updates are not proposed automatically.** The Dependency-Check stage now runs in every Jenkins pipeline (enabled 2026-11-12), but Dependabot covers only the dev container. | Fixes for vulnerable libraries arrive only when someone looks. | Add Dependabot entries for `maven`, `npm`, `docker` and `github-actions`; add the `nvd-api-key` credential to Jenkins so the scan stays fast |
| G2 | **No enforced review.** Most changes are merged locally into `main`. | Defects and security mistakes merge unreviewed; the change-control evidence PRODUCTS.md relies on is missing. | Protect `main`: require a pull request, one approval and the CI check |
| G3 | **GitHub Actions does not run SpotBugs** (`package` stops before `verify`) and uses `actions/checkout@v2`. | SAST findings only surface if Jenkins is running. | Change the step to `./mvnw verify`; update to `actions/checkout@v4` |
| G4 | **Architecture rules are not tested.** | D1 and D2 crept in without a failing build. | Add ArchUnit tests: domain has no Spring or Jakarta imports, no module depends on another service's domain |
| G5 | **No coverage measurement.** | Untested code is invisible; no trend to watch. | Add JaCoCo with a report per module; set a floor for `*-domain` once the baseline is known |
| G6 | **Stale guidance.** Jenkinsfile headers say infra and application layers have no tests (order-service infra has 16 test classes); `.github/copilot-instructions.md` describes modules (`application`, `domain`, `library`) and an in-memory repository that no longer exist. | Reviewers and coding assistants work from a wrong picture of the system. | Update the headers; rewrite or remove the instructions file |
| G7 | **Mockito self-attaches as a Java agent**, which the JDK warns will be disallowed. | Tests stop running on a future JDK. | Add Mockito as `-javaagent` in the Surefire configuration |
| G8 | **No image scan.** | Vulnerable OS packages in images. | Enable the Trivy stage |
| G9 | **Requirements traced only for products and orders.** | Other services' rules are verified, but not traceable to a stated need. | Extend the `PRD-…` approach where the rules matter |

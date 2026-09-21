<!-- BEGIN MICROSOFT SECURITY.MD V0.0.7 BLOCK -->

## Security

Microsoft takes the security of our software products and services seriously, which includes all source code repositories managed through our GitHub organizations, which include [Microsoft](https://github.com/Microsoft), [Azure](https://github.com/Azure), [DotNet](https://github.com/dotnet), [AspNet](https://github.com/aspnet), [Xamarin](https://github.com/xamarin), and [our GitHub organizations](https://opensource.microsoft.com/).

If you believe you have found a security vulnerability in any Microsoft-owned repository that meets [Microsoft's definition of a security vulnerability](https://aka.ms/opensource/security/definition), please report it to us as described below.

## Reporting Security Issues

**Please do not report security vulnerabilities through public GitHub issues.**

Instead, please report them to the Microsoft Security Response Center (MSRC) at [https://msrc.microsoft.com/create-report](https://aka.ms/opensource/security/create-report).

If you prefer to submit without logging in, send email to [secure@microsoft.com](mailto:secure@microsoft.com).  If possible, encrypt your message with our PGP key; please download it from the [Microsoft Security Response Center PGP Key page](https://aka.ms/opensource/security/pgpkey).

You should receive a response within 24 hours. If for some reason you do not, please follow up via email to ensure we received your original message. Additional information can be found at [microsoft.com/msrc](https://aka.ms/opensource/security/msrc). 

Please include the requested information listed below (as much as you can provide) to help us better understand the nature and scope of the possible issue:

  * Type of issue (e.g. buffer overflow, SQL injection, cross-site scripting, etc.)
  * Full paths of source file(s) related to the manifestation of the issue
  * The location of the affected source code (tag/branch/commit or direct URL)
  * Any special configuration required to reproduce the issue
  * Step-by-step instructions to reproduce the issue
  * Proof-of-concept or exploit code (if possible)
  * Impact of the issue, including how an attacker might exploit the issue

This information will help us triage your report more quickly.

If you are reporting for a bug bounty, more complete reports can contribute to a higher bounty award. Please visit our [Microsoft Bug Bounty Program](https://aka.ms/opensource/security/bounty) page for more details about our active programs.

## Preferred Languages

We prefer all communications to be in English.

## Policy

Microsoft follows the principle of [Coordinated Vulnerability Disclosure](https://aka.ms/opensource/security/cvd).

<!-- END MICROSOFT SECURITY.MD BLOCK -->

---

## OWASP Security Hardening Applied to This Project

This project has been hardened against the [OWASP Top 10 (2021)](https://owasp.org/Top10/) risks. The sections below document each finding category, the specific vulnerability identified, and the fix applied.

---

### A01 — Broken Access Control

**Finding:** Five `GET` endpoints accepted an `@RequestBody` containing the resource ID. HTTP GET with a request body is non-standard, bypasses caching layers, and some reverse proxies silently discard the body — causing 400 errors or unintended data exposure when the body is ignored.

**Fix:** Replaced `GET /get` + `@RequestBody` with `GET /{id}` using `@PathVariable` across all five services:
- `MedicalSalesRepController` → `GET /api/v1/medicalsalesrep/{id}`
- `HealthCareProfController` → `GET /api/v1/healthcareprof/{id}`
- `VisitController` → `GET /api/v1/visit/{id}`
- `VisitPlanController` → `GET /api/v1/visitplan/{id}`
- `SettlementController` → `GET /api/v1/settlement/{id}`

---

### A03 — Injection

**Finding 1 — Log Injection (CWE-117):** User-supplied values (IDs, filenames, names) were passed directly to `log.info()`. An attacker could inject `\r\n` sequences to forge log entries, pollute audit trails, or cause log-parsing failures.

**Fix:** A `sanitize()` helper was added to `SettlementController`, `VisitController`, and `VisitPlanController` that strips `\r`, `\n`, and `\t` before any value is written to the log:
```java
private static String sanitize(String value) {
    return value == null ? "" : value.replaceAll("[\r\n\t]", "_");
}
```

**Finding 2 — Unbounded Query Parameters (CWE-20):** The `pageSize` parameter on list/search endpoints accepted arbitrary integers. A large value (e.g. `pageSize=100000`) could trigger a full-table scan, exhausting database and heap resources.

**Fix:** A hard cap of 100 rows was enforced at the controller layer for `MedicalSalesRepController` and `HealthCareProfController`:
```java
pageSize = Math.min(pageSize, 100);
```
The same cap is applied inside `ListSettlementsUseCase`, `ListVisitsUseCase`, and `ListVisitPlansUseCase` at the domain layer, consistent with DDD clean architecture.

**Finding 3 — Weakly-Typed Date Input (OWASP ASVS 5.1 — Input Validation):** Accepting date fields as raw `String` and hand-parsing them downstream is a common source of format ambiguity (e.g. locale-dependent parsing) and lets malformed values propagate deep into the call stack before failing.

**Fix:** Every date-bearing input field is typed as `java.time.LocalDate`/`LocalDateTime` and parsed once, at the framework boundary, before any handler code runs:
- JSON request bodies: `@JsonFormat(pattern = "yyyy-MM-dd")` pins Jackson to one strict format (e.g. `AddInvoiceInputDTO`).
- Multipart/query/form parameters: `@DateTimeFormat(iso = DateTimeFormat.ISO.DATE)` applies the equivalent constraint through Spring's `ConversionService` — necessary because Jackson's `@JsonFormat` has no effect on `@RequestParam` binding, only on JSON body deserialization.

A malformed date can no longer reach a use case; it is rejected by the framework itself.

---

### A04 — Insecure Design / Unrestricted Resource Consumption (CWE-400)

**Finding 1:** `SettlementController.addInvoice()` called `MultipartFile.getBytes()`, which loads the entire file into the JVM heap in one shot. A large or maliciously crafted upload could exhaust memory and cause a denial-of-service.

**Fix:** Replaced `getBytes()` with streaming via `InputStream` and added a 10 MB size guard before the stream is read:
```java
if (file.getSize() > MAX_UPLOAD_BYTES) {
    throw new InvalidInputException("File exceeds the 10 MB limit.");
}
try (InputStream is = file.getInputStream()) { ... }
```

**Finding 2 — Business Invariants Enforced Only at the Edge (CWE-20 / OWASP ASVS 5.1 Positive Validation):** Rules such as "an invoice's issue date must fall within the last 60 days" or "a visit plan must be scheduled in the future" were previously re-checked ad hoc inside individual use cases. A new use case or a repository rehydration path could construct an entity in an invalid state simply by forgetting to repeat the check.

**Fix:** These rules were pushed down into immutable domain value objects (`IssueDate`, `DueDate`, `SettlementDate`, `VisitDateTime`) that validate in their constructor and throw `BusinessValidationException` on construction. Because `Invoice`, `Settlement`, `Visit`, and `VisitPlan` only accept these types — never a raw `LocalDate`/`LocalDateTime` — an invalid date can no longer reach the domain layer through any code path, present or future:
```java
public final class IssueDate extends DateValueObject {
    public IssueDate(LocalDate value) throws BusinessValidationException {
        super(value);
        if (value == null) {
            throw new BusinessValidationException("Issue date is required.");
        }
        if (value.isBefore(LocalDate.now().minusDays(60))) {
            throw new BusinessValidationException("Issue date must be within the last 60 days.");
        }
    }
}
```

---

### A05 — Security Misconfiguration

**Finding 1 — CORS Wildcard Headers:** All four `SecurityConfig` classes allowed `Access-Control-Allow-Headers: *`. This permits any custom header (including attacker-controlled headers) to be sent cross-origin, widening the attack surface for cross-origin attacks.

**Fix:** Replaced the wildcard with an explicit allowlist in every `SecurityConfig`:
```java
configuration.setAllowedHeaders(
    List.of("Authorization", "Content-Type", "X-Requested-With")
);
```

**Finding 2 — Swagger/OpenAPI Exposed in Production:** The Swagger UI and API-docs endpoints were reachable in all profiles, giving attackers a complete map of the API surface without authentication.

**Fix:**
- Annotated all five `OpenApiConfig` beans with `@Profile("!prod")` so they are not loaded in the `prod` Spring profile.
- Added a `swaggerEnabled` flag (bound to `springdoc.swagger-ui.enabled`) in each `SecurityConfig`; when false, the Swagger URL paths require full authentication instead of being permitted:
```java
@Value("${springdoc.swagger-ui.enabled:true}")
private boolean swaggerEnabled;
```
- Added `application-prod.properties` to `identity-service` with `springdoc.swagger-ui.enabled=false` and `springdoc.api-docs.enabled=false`.

**Finding 3 — Inconsistent Handling of Malformed Input (CWE-209 Information Exposure Through an Error Message):** Malformed JSON request bodies (e.g. an unparsable date) were caught by a dedicated handler and returned a clean `400`, but malformed `@RequestParam`/`@PathVariable` values — such as an invalid `issueDate` on the multipart `/invoice/add` endpoint — threw `MethodArgumentTypeMismatchException`, which had no dedicated handler and fell through to the catch-all `Exception` handler. This misreported a client input error as `500 Internal Server Error`, and depending on server configuration risks a framework-default error page leaking implementation details for exceptions not explicitly handled.

**Fix:** `GlobalExceptionHandler` now maps `MethodArgumentTypeMismatchException` to a `400 Bad Request` with a field-scoped message, matching the existing `HttpMessageNotReadableException`/`InvalidFormatException` handler. Every input-validation failure — JSON body, form param, query param, or path variable — now returns a consistent `400` shape. The catch-all `Exception` handler still logs full exception context server-side (`log.error("Unhandled exception", ex)`) but returns only a generic `"An unexpected error occurred."` message to the client, never a stack trace:
```java
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
@ResponseStatus(HttpStatus.BAD_REQUEST)
public ResponseEntity<Map<String, String>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
    Map<String, String> errors = new HashMap<>();
    Class<?> requiredType = ex.getRequiredType();
    String targetType = requiredType != null ? requiredType.getSimpleName() : "unknown";
    errors.put(ex.getName(), "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName()
            + "'. Expected type: " + targetType + ".");
    return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
}
```

---

### A08 — Software and Data Integrity Failures / Unrestricted File Upload (CWE-434)

**Finding:** `VisitController` (attachment upload) and `SettlementController` (invoice upload) accepted files of any MIME type and extension. An attacker could upload executable scripts, polyglot files, or malware.

**Fix:** An explicit extension allowlist was enforced before the file is processed:
```java
private static final Set<String> ALLOWED_EXTENSIONS =
    Set.of(".pdf", ".xlsx", ".docx", ".txt");

if (!ALLOWED_EXTENSIONS.contains(ext)) {
    throw new InvalidInputException(
        "Unsupported file type '" + ext + "'. Allowed: .pdf, .xlsx, .docx, .txt");
}
```

---

### Dependency CVEs Fixed

| Dependency | CVE | CVSS | Fix |
|---|---|---|---|
| `snakeyaml` 1.20-android (transitive via `javafaker`) | CVE-2022-1471 | 9.8 | Excluded from `domain-commons/pom.xml` |
| `commons-lang3` (transitive via `javafaker`) | CVE-2025-48924 | — | Excluded from `domain-commons/pom.xml` |

```xml
<dependency>
    <groupId>com.github.javafaker</groupId>
    <artifactId>javafaker</artifactId>
    <version>0.16</version>
    <exclusions>
        <exclusion>
            <groupId>org.yaml</groupId>
            <artifactId>snakeyaml</artifactId>
        </exclusion>
        <exclusion>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

---

### Pagination Hardening (DDD Clean Architecture)

The three "list all" use cases (`ListSettlementsUseCase`, `ListVisitsUseCase`, `ListVisitPlansUseCase`) previously returned unbounded result sets with no pagination. This was refactored to follow DDD clean architecture conventions:

- New input DTOs with `@Min`/`@Max` constraints enforce safe page bounds at the domain boundary.
- Repository interfaces extended with `searchAll(int page, int pageSize)`.
- Infrastructure implementations use `PageRequest.of(page - 1, pageSize)` (Spring Data JPA).
- Controllers cap `pageSize` at 100 before delegating to the use case, preventing resource exhaustion even if validation is bypassed upstream.

# Create VisitPlan — sequence diagram

`POST /api/v1/visitplan/create` in visit-service: a Medical Sales Rep plans a visit to a Healthcare Professional.
Request body: `visitDateTime` (future, `yyyy-MM-dd'T'HH:mm:ss`), `healthCareProfId`, `visitSiteId`, optional
`visitComments`, optional `medicalSalesRepId` (must be the caller's own) and optional `items` (products to bring).

```mermaid
sequenceDiagram
    autonumber
    actor MSR as Medical Sales Rep
    participant GW as API Gateway
    participant JWT as JwtAuthenticationFilter
    participant C as VisitPlanController
    participant UC as CreateVisitPlanUseCase
    participant Repo as SQLVisitPlanRepository
    participant F as VisitPlanFactory
    participant HV as HealthCareProfValidatorAdapter
    participant MV as MedicalSalesRepValidatorAdapter
    participant HCP as healthcare-prof-service
    participant MSRS as medical-sales-rep-service
    participant DB as SQL Server (visitdb)

    MSR->>GW: POST /api/v1/visitplan/create (Bearer JWT)
    GW->>JWT: route lb://visit-service
    JWT->>JWT: verify signature, ROLE_USER, msr_id claim
    alt token invalid or not ROLE_USER
        JWT-->>MSR: 401 / 403
    end
    JWT->>C: createVisitPlan(body, authentication)
    C->>C: @Valid body (@Future date, required ids, max items)
    alt body invalid
        C-->>MSR: 400 field errors
    end
    C->>C: CallerMsr.require(auth, body.medicalSalesRepId)
    alt caller has no msr_id, or body names another MSR
        C-->>MSR: 403 Access denied
    end
    C->>UC: execute(CreateVisitPlanInputDTO with caller's msrId)
    UC->>UC: required fields, build value objects
    UC->>Repo: existsDuplicateOnDay(date, hcpId, msrId)
    Repo->>DB: SELECT plans for HCP + MSR that day
    DB-->>Repo: exists?
    alt a plan already exists that day
        UC-->>MSR: 400 "A visit plan ... already exists on the same day."
    end
    UC->>F: createVisitPlan(dateTime, hcpId, comments, siteId, msrId, items)
    F->>F: date not in the past
    F->>HV: existsAndActive(hcpId)
    alt cached confirmed active
        HV-->>F: true (hcpActiveStatus cache)
    else
        HV->>HCP: GET /api/v1/healthcareprof/{id}/active-status (Eureka)
        alt service answers
            HCP-->>HV: {active}
            HV->>DB: upsert HcpSnapshot(active)
        else service unreachable
            HV->>DB: read HcpSnapshot (last known, none = false)
        end
        HV-->>F: active
    end
    F->>MV: existsAndActive(msrId)
    Note over MV,MSRS: same pattern: msrActiveStatus cache,<br/>GET /api/v1/medicalsalesrep/{id}/active-status, MsrSnapshot fallback
    MV-->>F: active
    alt HCP or MSR not found / inactive
        F-->>MSR: 400 "... not found or not active."
    end
    F-->>UC: new VisitPlan(random VisitId, active = true)
    UC->>Repo: save(visitPlan)
    Repo->>DB: INSERT visit plan and its items
    UC-->>C: VisitPlanOutputDTO
    C-->>MSR: 201 Created (visit plan)
```

## Notes

- **Ownership.** A visit plan always belongs to the caller's own MSR, taken from the token's `msr_id`. A body naming
  another MSR is refused, never silently reassigned (OWASP A01).
- **Anti-corruption layer.** visit-service only asks HCP and MSR for an `active` boolean, with no personal data and
  no forwarded user JWT. Only a confirmed `true` is cached, so a transient failure is never cached as "inactive".
- **Resilience.** When HCP or MSR cannot be reached, the local snapshot's last-known flag decides; with no snapshot
  the check fails closed (`false` → `400`).
- **Errors.** Domain and validation errors answer `400` with their message (`GlobalExceptionHandler`); authorization
  errors answer `403 Access denied`.
- **No events.** Creating a plan writes one row through JPA and publishes nothing to the broker.

Code: [VisitPlanController](../../visit-service/visit-application/src/main/java/com/das/visit/application/VisitPlanController.java) ·
[CreateVisitPlanUseCase](../../visit-service/visit-domain/src/main/java/com/das/cleanddd/domain/visit/usecases/services/CreateVisitPlanUseCase.java) ·
[VisitPlanFactory](../../visit-service/visit-domain/src/main/java/com/das/cleanddd/domain/visit/entities/VisitPlanFactory.java) ·
[HealthCareProfValidatorAdapter](../../visit-service/visit-infra/src/main/java/com/das/infra/service/visit/HealthCareProfValidatorAdapter.java)

# Create Visit — sequence diagram

`POST /api/v1/visit/create` in visit-service: a Medical Sales Rep records a visit made to a Healthcare Professional.
Request body: `visitDate` (`yyyy-MM-dd`), `healthCareProfId`, `visitSiteId`, optional `visitComments`, optional
`medicalSalesRepId` (must be the caller's own) and optional `items` (products handed out).

```mermaid
sequenceDiagram
    autonumber
    actor MSR as Medical Sales Rep
    participant GW as API Gateway
    participant JWT as JwtAuthenticationFilter
    participant C as VisitController
    participant UC as CreateVisitUseCase
    participant HV as HealthCareProfValidatorAdapter
    participant MV as MedicalSalesRepValidatorAdapter
    participant HCP as healthcare-prof-service
    participant MSRS as medical-sales-rep-service
    participant F as VisitFactory
    participant Repo as SQLVisitRepository
    participant DB as SQL Server (visitdb)

    MSR->>GW: POST /api/v1/visit/create (Bearer JWT)
    GW->>JWT: route lb://visit-service
    JWT->>JWT: verify signature, ROLE_USER, msr_id claim
    alt token invalid or not ROLE_USER
        JWT-->>MSR: 401 / 403
    end
    JWT->>C: createVisit(body, authentication)
    C->>C: @Valid body (visitDate required, max items)
    alt body invalid
        C-->>MSR: 400 field errors
    end
    C->>C: CallerMsr.require(auth, body.medicalSalesRepId)
    alt caller has no msr_id, or body names another MSR
        C-->>MSR: 403 Access denied
    end
    C->>UC: execute(CreateVisitInputDTO with caller's msrId)
    UC->>UC: required fields, build value objects
    UC->>HV: existsAndActive(hcpId)
    alt cached confirmed active
        HV-->>UC: true (hcpActiveStatus cache)
    else
        HV->>HCP: GET /api/v1/healthcareprof/{id}/active-status (Eureka)
        alt service answers
            HCP-->>HV: {active}
            HV->>DB: upsert HcpSnapshot(active)
        else service unreachable
            HV->>DB: read HcpSnapshot (last known, none = false)
        end
        HV-->>UC: active
    end
    alt HCP not found or inactive
        UC-->>MSR: 400 "Health Care Professional not found or not active"
    end
    UC->>MV: existsAndActive(msrId)
    Note over MV,MSRS: same pattern: msrActiveStatus cache,<br/>GET /api/v1/medicalsalesrep/{id}/active-status, MsrSnapshot fallback
    MV-->>UC: active
    alt MSR not found or inactive
        UC-->>MSR: 400 "Medical Sales Representative not found or not active"
    end
    UC->>Repo: existsByVisitKey(hcpId, msrId, visitDate at 00:00)
    Repo->>DB: SELECT visit with the same HCP, MSR and date
    DB-->>Repo: exists?
    alt a visit already exists
        UC-->>MSR: 400 "A visit already exists for this ... and date."
    end
    UC->>F: createVisit(date, hcpId, comments, siteId, msrId, items)
    F-->>UC: new Visit(random VisitId)
    UC->>Repo: save(visit)
    Repo->>DB: INSERT visit and its items
    UC-->>C: VisitOutputDTO
    C-->>MSR: 201 Created (visit)
```

## Notes

- **Visit vs. visit plan.** A visit records what happened, so its date is a day (`yyyy-MM-dd`, stored at 00:00) and
  may be in the past. A [visit plan](create-visit-plan.md) is a future appointment with a time.
- **Order of checks.** Here the use case checks the HCP and MSR first and the duplicate second. Create VisitPlan
  does the opposite, and runs the HCP/MSR checks inside `VisitPlanFactory`.
- **One visit per key.** HCP + MSR + date identifies a visit.
- **Ownership, ACL and resilience** work as for visit plans: the MSR comes from the token's `msr_id`, the HCP and MSR
  services only answer an `active` boolean, only a confirmed `true` is cached, and the local snapshot decides when
  a service is unreachable (no snapshot → refused).
- **Errors.** Domain and validation errors answer `400` with their message; authorization errors `403 Access denied`.
- **No events.** Creating a visit writes through JPA and publishes nothing to the broker.

Code: [VisitController](../../visit-service/visit-application/src/main/java/com/das/visit/application/VisitController.java) ·
[CreateVisitUseCase](../../visit-service/visit-domain/src/main/java/com/das/cleanddd/domain/visit/usecases/services/CreateVisitUseCase.java) ·
[VisitFactory](../../visit-service/visit-domain/src/main/java/com/das/cleanddd/domain/visit/entities/VisitFactory.java) ·
[SQLVisitRepository](../../visit-service/visit-infra/src/main/java/com/das/infra/service/visit/SQLVisitRepository.java)

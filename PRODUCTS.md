# Products: GS1, GxP and GAMP 5 considerations

How product-catalog-service and order-service handle the products a Pharma Lab supplies to its Medical Sales Reps
(MSRs), and which regulation or standard each rule comes from. It is written for developers changing these services
and for the Quality, validation and regulatory people who review them.

- [Scope](#scope)
- [The three frameworks](#the-three-frameworks)
- [Requirements and where they are met](#requirements-and-where-they-are-met)
  - [Identification (GS1)](#identification-gs1)
  - [Lots, expiry and delivery (GDP)](#lots-expiry-and-delivery-gdp)
  - [Quarantine, rejection and recall (GDP)](#quarantine-rejection-and-recall-gdp)
  - [Storage and transport conditions (GDP)](#storage-and-transport-conditions-gdp)
  - [Data integrity and audit trail (ALCOA+, Annex 11, Part 11)](#data-integrity-and-audit-trail-alcoa-annex-11-part-11)
  - [Access control](#access-control)
- [GAMP 5 view of the system](#gamp-5-view-of-the-system)
- [Gaps and what is out of scope](#gaps-and-what-is-out-of-scope)
- [Changing these rules](#changing-these-rules)

> The regulatory references are given to explain why each rule exists. They are not a compliance claim: confirm them
> with your Quality and Regulatory teams, and validate the system for its intended use before relying on it for GxP
> decisions.

## Scope

| In scope | Not in scope |
|---|---|
| Product master data: name, price, unit, GTIN, lot tracking, storage conditions | Manufacturing, batch release by a Qualified Person |
| Stock held in lots (batch + expiry), goods receipt, FEFO allocation | Warehouse management (locations, picking) |
| Quarantine, rejection, recall lookups | Temperature monitoring hardware, mapping, alarms |
| Excursions reported by a logger or monitoring system | Pack-level serialisation and verification (EU FMD, DSCSA) |
| Orders from MSRs, stock reservation, delivery with a transport check | Shipping, carriers, qualified packaging |
| Audit trail of every change | Backups, archiving, disaster recovery (infrastructure) |

Products are of two kinds:

- **Medicines and drug samples** are **lot-tracked**: every unit belongs to a batch with an expiration date, and the
  product must have storage conditions.
- **Promotional material** (brochures, leaflets) is created with `lotTracked: false`. Batch, expiry and storage
  conditions are optional for it, because the medicine traceability rules don't apply.

## The three frameworks

| Framework | What it is | What it asks of this system |
|---|---|---|
| **GS1** | A global standard for identifying products and logistic units (GTIN, Application Identifiers, DataMatrix, GDSN, EPCIS). Not a regulation, but the identifiers regulators expect on medicine packs. | Identify each trade item by one GTIN; read batch and expiry from the pack's code; keep identifiers exactly as printed. |
| **GxP**, here mainly **GDP** (Good Distribution Practice) | Regulations and guidelines for distributing medicines: EU GDP Guidelines 2013/C 343/01, WHO TRS 961 Annex 9, 21 CFR 211 and, for drug samples, 21 CFR 203. Data integrity: EU GMP Annex 11, 21 CFR Part 11, MHRA and PIC/S guidance (ALCOA+). | Traceable lots, FEFO, no expired or quarantined stock delivered, recall, storage and transport within the label's conditions, excursions recorded and decided by Quality, a complete audit trail. |
| **GAMP 5** (ISPE, 2nd edition 2022) | A risk-based approach to validating computerised systems used in GxP work. | Know which functions are GxP-critical, design and test them in proportion to risk, keep records trustworthy, and control change. |

## Requirements and where they are met

Each requirement has an id (`PRD-…`) so it can be traced to its tests and cited in a validation plan or change
request.

### Identification (GS1)

| Id | Requirement | Basis | Implementation | Verified by |
|---|---|---|---|---|
| PRD-01 | A GTIN has 8, 12, 13 or 14 digits and a valid mod-10 check digit; it is stored as 14 digits. | GS1 General Specifications | `Gtin` | `GtinTest` |
| PRD-02 | One GTIN identifies one product. Once set, it can't be changed or removed; a different item gets a new product. | GS1 GTIN Management Standard | `Product.withUpdatedDetails`, `ProductUseCaseSupport.requireUnusedGtin` | `StockLotUseCasesTest.GtinRules` |
| PRD-03 | Goods receipt reads the pack's GS1 element string, human-readable or raw scanner output: AI 01 GTIN, 10 batch, 17 expiry, 11 production date, 21 serial. Anything else, a repeated AI or malformed data is refused, never guessed. | GS1 General Specifications | `Gs1ElementString` | `Gs1ElementStringTest` |
| PRD-04 | The scanned GTIN must be the product's GTIN, and typed values must agree with the code, so the wrong item can't be booked in. | GDP 5.2 (receipt checks) | `ReceiveStockLotUseCase` | `StockLotUseCasesTest.GoodsReceipt` |
| PRD-05 | Batch numbers use GS1 character set 82, 1–20 characters, kept exactly as printed (no trimming or re-casing). | GS1 AI 10; ALCOA+ *Original* | `BatchNumber` | `StockLotUseCasesTest`, `Gs1ElementStringTest` |
| PRD-06 | Two-digit years follow the GS1 century rule; day `00` means the last day of the month. | GS1 General Specifications 7.12 | `Gs1ElementString` | `Gs1ElementStringTest.dates` |

### Lots, expiry and delivery (GDP)

| Id | Requirement | Basis | Implementation | Verified by |
|---|---|---|---|---|
| PRD-10 | A medicine's stock is only ever held in lots with a batch number and expiration date. One batch has one expiration date. | GDP 5.5; EU FMD / DSCSA traceability | `StockLot`, `ReceiveStockLotUseCase`, `CreateProductUseCase` | `StockLotUseCasesTest.GoodsReceipt`, `StockLotsSchemaScriptTest` |
| PRD-11 | Expired goods are refused at receipt. | GDP 5.2 | `ReceiveStockLotUseCase` | `StockLotUseCasesTest.requiredAndNotExpired` |
| PRD-12 | Orders take units first expiry, first out (FEFO). A line may span several lots, and the lots used are stored with the reservation. | GDP 5.5 | `StockAllocator`, `ReserveStockForOrderUseCase` | `StockLotUseCasesTest.Allocation`, `StockReservationSagaTest` |
| PRD-13 | Never delivered: quarantined, rejected or expired lots, or lots inside the minimum remaining shelf life (`catalog.stock.min-remaining-shelf-life-days`). | GDP 5.5, 6.1 | `StockLot.isAllocatable`, `ShelfLifePolicy` | `StockLotUseCasesTest.QuarantineAndRecall` |
| PRD-14 | A reservation is all or nothing; releasing it gives each unit back to the lot it came from. | Traceability | `ReserveStockForOrderUseCase`, `ReleaseStockForOrderUseCase` | `StockReservationSagaTest`, `OrderEventHandlerTest` |

### Quarantine, rejection and recall (GDP)

| Id | Requirement | Basis | Implementation | Verified by |
|---|---|---|---|---|
| PRD-20 | Quality can quarantine a lot and lift the quarantine, each with a reason. Quarantined units stay on hand but are not delivered. | GDP 5.5, 6.3 | `ChangeLotQuarantineUseCase` | `StockLotUseCasesTest.QuarantineAndRecall` |
| PRD-21 | Quality can reject a quarantined lot, with a reason. Rejection is final: the lot can't be delivered, released or received into, and its units leave the stock on hand, but stay on the lot for reconciliation with the destruction record. | GDP 6.3, 5.9 (destruction) | `RejectLotUseCase`, `StockLotJpaRepository.onHand` | `StorageAndExcursionTest.QualityDecision`, `StockLotRepositoryTest.aRejectedLotIsNoLongerOnHand` |
| PRD-22 | Recall: list every order that received units of a lot, with the reservation's status. | GDP 6.5 | `FindLotRecipientsUseCase` | `StockLotUseCasesTest.recallListsRecipients` |

### Storage and transport conditions (GDP)

| Id | Requirement | Basis | Implementation | Verified by |
|---|---|---|---|---|
| PRD-30 | A medicine has storage conditions from its label: temperature range in °C (at most one decimal), optional humidity limit, protect from light. Creating one without them is refused. | GDP 3.2, 5.6; 21 CFR 211.142; 21 CFR 203.32 (samples); GS1 GDSN temperature attributes | `StorageCondition` (domain-commons), `CreateProductUseCase` | `StorageConditionTest`, `CreateProductUseCaseTest.storageConditionsAreRequiredForMedicines` |
| PRD-31 | A change of storage conditions is audited with its previous value and reason. A medicine's conditions can't be removed. | ALCOA+; Annex 11 §9 | `Product.withUpdatedDetails` | `StorageAndExcursionTest.changeIsAudited` |
| PRD-32 | Every excursion record from a logger or monitoring system is kept, within the conditions or not, with its source, period and readings. | GDP 3.2.1, 9.2 | `RecordExcursionUseCase`, event `catalog.product.lot-excursion-recorded` | `StorageAndExcursionTest.Excursions` |
| PRD-33 | A reading outside the conditions puts an available lot in quarantine at once; the reason states what was read against what is allowed. | GDP 9.2 (excursions investigated, stock not used until decided) | `RecordExcursionUseCase` | `StorageAndExcursionTest.breachQuarantines`, catalog `OutboxWiringTest.aBreachOfTheStorageConditionsQuarantinesTheLotAndQualityRejectsIt` |
| PRD-34 | Each reservation line, and then each order line, carries the product's conditions at reservation time. | GDP 9.1 (transport keeps the label conditions) | `ReservationLine`, `catalog.reservation.confirmed`, `OrderLine` | `StorageAndExcursionTest.reservationCarriesConditions`, `StockReservationIntegrationEventsTest`, `CatalogEventTranslatorTest` |
| PRD-35 | Confirming delivery of an order with conditions needs the receiver's transport report (conditions kept, optional logger readings, note). | GDP 9.2, 5.2 | `ConfirmOrderDeliveryUseCase`, `Order.markDelivered` | `OrderTransportTest.reportRequired`, `TransportAndQualityDecisionUseCaseTest` |
| PRD-36 | A break, reported or read, quarantines the delivery (`DELIVERY_QUARANTINED`); Quality releases or rejects it with a reason, and who decided is kept. | GDP 6.3, 9.2 | `Order.releaseDelivery`, `Order.rejectDelivery`, `DecideDeliveryUseCase` | `OrderTransportTest`, `TransportSchemaScriptTest`, order `OutboxWiringTest` |

### Data integrity and audit trail (ALCOA+, Annex 11, Part 11)

| Id | Requirement | Basis | Implementation | Verified by |
|---|---|---|---|---|
| PRD-40 | Every change writes an audit entry in the same transaction: who (`actor`), when (`occurred_at`, UTC), what (`payload`), the record and its change number. | ALCOA+; Annex 11 §9; Part 11 §11.10(e) | `audit_event`, `Outbox*EventPublisher` | `OutboxProductEventPublisherTest`, `UnitOfWorkAtomicityTest` |
| PRD-41 | Every edit records its reason and each changed field's previous and new value. | Annex 11 §9; Part 11 §11.10(e) | `ChangeReason`, `FieldChanges` (domain-commons) | `FieldChangesTest`, `ChangeReasonTest`, `StockLotUseCasesTest.anEditNeedsAReason` |
| PRD-42 | The audit trail is append-only: the application can only insert and read, the database refuses `UPDATE`, `DELETE` and `TRUNCATE`, and the service connects as a user that owns nothing. | ALCOA+ *Enduring*; Annex 11 §7 | `audit-postgresql.sql`, `app-user-postgresql.sql` | `OutboxSchemaScriptTest`; manual check in README |
| PRD-43 | Admins can review a product's, reservation's or order's history. | Annex 11 §9 (review) | `ProductHistoryController`, `OrderHistoryController` | `ProductControllerSecurityTest`, `OrderControllerSecurityTest` |
| PRD-44 | Values are kept exactly as entered or refused; nothing is silently corrected. | ALCOA+ *Original*, *Accurate* | value objects (`Gtin`, `BatchNumber`, `StorageCondition`) | value object tests |

### Access control

| Action | Role | Why |
|---|---|---|
| Create, update, activate, deactivate products | `ADMIN` | Master data is controlled (Annex 11 §12) |
| Goods receipt, manual reserve/release | `AGENT` (AI Agent Service) | Stock changes only through a controlled interface |
| Record excursions | `ADMIN` or `AGENT` (monitoring integration) | Readings come from Quality or a validated monitoring system |
| Quarantine, lift, reject a lot; recall lookups | `ADMIN` | Quality decisions by authorised people (GDP 2.2) |
| Approve or reject an order | `AGENT` | Approver is the token's principal, never the request |
| Confirm delivery | the order's own MSR, or `AGENT` | An MSR can't confirm someone else's order |
| Release or reject a quarantined delivery | `ADMIN` | Quality decision |
| Read audit history | `ADMIN` | Review |

Rules are in each service's `SecurityConfig` and verified by `ProductControllerSecurityTest` and
`OrderControllerSecurityTest`.

## GAMP 5 view of the system

**Software category.**

| Part | GAMP 5 category | Notes |
|---|---|---|
| product-catalog-service, order-service, domain-commons | 5 (custom application) | Highest validation effort: requirements, design, code review, testing |
| Spring Boot, Hibernate, PostgreSQL, RabbitMQ, Redis | 1 (infrastructure software) | Qualified as part of the platform; versions controlled in `pom.xml` and the compose files |
| Jenkins, SpotBugs, OWASP Dependency-Check | Tools | Support the lifecycle; they don't hold GxP records |
| Temperature loggers and the monitoring system | 3 or 4, validated separately | This system receives their readings; it doesn't replace them |

**GxP-critical functions** (highest risk; changes need the most scrutiny):

1. Whether a lot can be delivered (PRD-13, PRD-20, PRD-21, PRD-33): a wrong answer lets unusable stock reach an MSR.
2. Identifying the item and batch at receipt (PRD-01 to PRD-05): a wrong answer breaks traceability and recall.
3. Recording and deciding excursions and transport breaks (PRD-32 to PRD-36).
4. The audit trail (PRD-40 to PRD-42).

**How the lifecycle supports validation.**

- **Requirements to tests.** The tables above trace each requirement to the automated tests that verify it. They run
  on every build.
- **Business rules in the domain.** The rules live in the domain layer, with no framework, so they are tested
  directly (`*-domain` tests) apart from the database and HTTP layers.
- **Database changes.** Each change ships as an idempotent SQL script, and a test runs it against the previous schema
  with Hibernate's `validate`: `StockLotsSchemaScriptTest`, `SagaSchemaScriptTest`, `TransportSchemaScriptTest`.
- **CI gates.** Each service's Jenkins pipeline runs the domain, infra and application tests, SpotBugs with Find
  Security Bugs, and OWASP Dependency-Check, which fails the build on CVSS 7 or above. See
  [CI with Jenkins](README.md#ci-with-jenkins).
- **Change control.** Every change is a reviewed commit on a feature branch, merged into `main`. The git history and
  this document record what changed and why.

**Still the validation team's work** (not in the repository): the validation plan, user requirements signed off by
the process owner, the risk assessment, IQ/OQ/PQ or their CSA equivalents on the target environment, supplier
assessments for the infrastructure, periodic review, and the validation report.

## Gaps and what is out of scope

| Gap | Impact | Possible next step |
|---|---|---|
| **No electronic signature** on Quality decisions (reject a lot, release or reject a delivery). They are authenticated and audited, but not signed. | Part 11 §11.50 / §11.200 and Annex 11 §14 expect a signature with its meaning where a signature is required by the predicate rule. | Re-authentication with a stated meaning ("Rejected by Quality") before the decision is stored. |
| **No pack serialisation.** AI 21 is read but not stored; stock is per lot. | EU FMD (Delegated Regulation 2016/161) and US DSCSA need serial-level verification and decommissioning for prescription medicines. | Integrate with the national verification system (EMVS) or a DSCSA partner instead of storing serials here. |
| **Excursions are posted per lot as JSON,** not as GS1 EPCIS 2.0 sensor events. | A monitoring system needs an adapter, and one fridge holding many lots means many calls. | Accept EPCIS 2.0 `sensorElementList` for a location and resolve the lots held there. |
| **GS1 temperature AIs (4330–4333) are not read** from labels. | Conditions are entered by hand from the label. | Accept them in `Gs1ElementString` and compare them with the product's conditions. |
| **Storage by the MSR** (car, home) is not recorded. | A known GDP risk for drug samples (21 CFR 203.32). | Record each MSR's storage location and a periodic confirmation of its conditions. |
| **Existing medicines** created before storage conditions have none until an update adds them. | Readings can't be recorded for them, and their orders need no transport report. | Add the conditions to every medicine with `PUT /api/v1/products/update`; consider refusing restocks of medicines without them. |
| **Status changes** (activate, deactivate, approve) don't ask for a reason. | Weaker audit trail for those events. | Require a reason as for edits. |
| **Under the `dev` profile** HCP, MSR, catalog and order write no events, so nothing is audited. | Dev data is not GxP data. | Never use the `dev` profile for GxP work. |
| **Backups, retention, time synchronisation** are set up outside this repository. | Annex 11 §7, §17. | Cover them in the infrastructure qualification. |

## Changing these rules

When you change anything in the tables above:

1. Update the requirement here, keeping its id. Add new ids instead of reusing old ones.
2. Add or update the tests named in *Verified by*. A requirement without a test is a gap; add it to [Gaps](#gaps-and-what-is-out-of-scope).
3. Ship database changes as an idempotent script with a schema test, and list it in the README's upgrade steps.
4. Keep published event changes additive, so the schema version stays 1 and other services keep working.
5. Tell Quality: a change to a GxP-critical function may need a change control record and re-validation.

See also: [README, Product Catalog Service](README.md#product-catalog-service--apiv1products) ·
[Storage and transport conditions](README.md#storage-and-transport-conditions) ·
[Audit trail (ALCOA+)](README.md#audit-trail-alcoa) · [Order stock saga](docs/diagrams/order-saga.md)

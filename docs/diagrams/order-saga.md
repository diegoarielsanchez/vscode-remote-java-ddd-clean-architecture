# Order stock saga — sequence diagram

A Medical Sales Rep orders products from the Pharma Lab. order-service accepts the order at once; product-catalog-service
reserves the stock asynchronously over RabbitMQ; the AI Agent Service approves or rejects; rejecting gives the stock
back (the compensating step); delivery fulfils the reservation.

Both services use a **transactional outbox**: a state change and the events it raises commit in one database
transaction, and a relay publishes the outbox rows to the broker afterwards. Each consumer records the `eventId` it
processed, so a redelivered message changes nothing.

## Order status

```mermaid
stateDiagram-v2
    [*] --> AWAITING_STOCK: POST /create
    AWAITING_STOCK --> PENDING_APPROVAL: catalog.reservation.confirmed
    AWAITING_STOCK --> STOCK_REJECTED: catalog.reservation.rejected
    PENDING_APPROVAL --> APPROVED: POST /{id}/approve (AGENT)
    PENDING_APPROVAL --> REJECTED: POST /{id}/reject (AGENT)
    APPROVED --> DELIVERED: POST /{id}/confirm-delivery (conditions kept)
    APPROVED --> DELIVERY_QUARANTINED: POST /{id}/confirm-delivery (transport break)
    DELIVERY_QUARANTINED --> DELIVERED: POST /{id}/delivery/release (ADMIN)
    DELIVERY_QUARANTINED --> DELIVERY_REJECTED: POST /{id}/delivery/reject (ADMIN)
    STOCK_REJECTED --> [*]
    REJECTED --> [*]
    DELIVERED --> [*]
    DELIVERY_REJECTED --> [*]
```

## Sequence

```mermaid
sequenceDiagram
    autonumber
    actor MSR as Medical Sales Rep
    actor Agent as AI Agent Service
    participant OC as order-service<br/>OrderController
    participant OUC as order use cases
    participant ODB as order_db<br/>(orders + outbox)
    participant ORelay as OrderOutboxRelay
    participant MQ as RabbitMQ
    participant CH as catalog<br/>OrderEventHandler
    participant CUC as catalog reservation<br/>use cases
    participant CDB as productcatalog_db<br/>(lots, reservations, outbox)
    participant CRelay as ProductOutboxRelay
    participant OH as order<br/>CatalogEventHandler

    rect rgba(127, 127, 127, 0.08)
    Note over MSR,OH: 1. Create order (synchronous part)
    MSR->>OC: POST /api/v1/orders/create {lines} (JWT with msr_id)
    OC->>OC: caller's msr_id (a body MSR must match, else 403)
    OC->>OUC: CreateOrderUseCase
    OUC->>OUC: MSR exists and active (msr-service, sync)
    alt MSR unknown or inactive, or no lines
        OUC-->>MSR: 400
    end
    OUC->>ODB: BEGIN, INSERT order AWAITING_STOCK + outbox order.created, COMMIT
    OC-->>MSR: 202 Accepted, Location /api/v1/orders/{id}
    end

    rect rgba(127, 127, 127, 0.08)
    Note over MSR,OH: 2. Reserve stock (asynchronous)
    ORelay->>ODB: poll unpublished outbox rows (every 1 s)
    ORelay->>MQ: publish order.created to order.events (publisher confirm)
    ORelay->>ODB: mark published after the broker's ack
    MQ->>CH: catalog-service.order.queue
    CH->>CDB: BEGIN, eventId already processed?
    alt duplicate
        CH-->>MQ: ack, nothing changes
    end
    CH->>CUC: ReserveStockForOrderUseCase(order lines)
    CUC->>CDB: take every line from deliverable lots (FEFO, all or nothing)
    alt every line covered
        CUC->>CDB: reservation RESERVED with its lot allocations<br/>+ outbox stock-reserved, catalog.reservation.confirmed (names, unit prices, storage conditions)
    else a line is short, or a product is inactive
        CUC->>CDB: give back what was taken, reservation REJECTED<br/>+ outbox catalog.reservation.rejected (reason)
    end
    CH->>CDB: INSERT processed_event, COMMIT
    CRelay->>MQ: publish to catalog.events (publisher confirm)
    MQ->>OH: order-service.catalog.queue
    OH->>ODB: BEGIN, eventId already processed?
    alt confirmed
        OH->>OUC: ConfirmOrderStockUseCase(prices)
        OUC->>ODB: order PENDING_APPROVAL with priced lines and their storage conditions<br/>+ outbox order.submitted-for-approval
    else rejected
        OH->>OUC: RejectOrderForStockUseCase(reason)
        OUC->>ODB: order STOCK_REJECTED + outbox order.stock-rejected
    end
    OH->>ODB: INSERT processed_event, COMMIT
    MSR->>OC: GET /api/v1/orders/{id} (poll)
    OC-->>MSR: PENDING_APPROVAL or STOCK_REJECTED
    end

    rect rgba(127, 127, 127, 0.08)
    Note over MSR,OH: 3a. Approve
    Agent->>OC: POST /api/v1/orders/{id}/approve (ROLE_AGENT)
    OC->>OUC: ApproveOrderUseCase(approvedBy = token principal)
    OUC->>ODB: order APPROVED + outbox order.approved
    OC-->>Agent: 200
    Note right of MQ: catalog ignores order.approved:<br/>the stock stays reserved
    end

    rect rgba(127, 127, 127, 0.08)
    Note over MSR,OH: 3b. Reject (compensation)
    Agent->>OC: POST /api/v1/orders/{id}/reject {reason} (ROLE_AGENT)
    OC->>OUC: RejectOrderUseCase(rejectedBy = token principal)
    OUC->>ODB: order REJECTED + outbox order.rejected
    OC-->>Agent: 200
    ORelay->>MQ: order.rejected
    MQ->>CH: catalog-service.order.queue
    CH->>CUC: ReleaseStockForOrderUseCase
    CUC->>CDB: give each allocation back to its own lot, reservation RELEASED<br/>+ outbox stock-released, catalog.reservation.released
    Note right of OH: order-service ignores catalog.reservation.released
    end

    rect rgba(127, 127, 127, 0.08)
    Note over MSR,OH: 4. Deliver (after 3a)
    MSR->>OC: POST /api/v1/orders/{id}/confirm-delivery {transport report} (own order, or ROLE_AGENT)
    OC->>OUC: ConfirmOrderDeliveryUseCase (idempotent)
    alt conditions kept (or none to keep)
        OUC->>ODB: order DELIVERED + outbox order.delivered
    else transport break
        OUC->>ODB: order DELIVERY_QUARANTINED with the breaches + outbox order.delivery-quarantined
        Note over MSR,OH: Quality: POST /{id}/delivery/release → DELIVERED + order.delivered,<br/>or /delivery/reject → DELIVERY_REJECTED + order.delivery-rejected
    end
    OC-->>MSR: 200
    ORelay->>MQ: order.delivered
    MQ->>CH: catalog-service.order.queue
    CH->>CUC: FulfilStockReservationUseCase (also on order.delivery-rejected)
    CUC->>CDB: reservation FULFILLED (units leave stock for good)
    end
```

## Notes

- **Why a saga.** Orders and stock live in different services and databases, so there is no shared transaction. Each
  step commits locally and announces itself with an event; a rejection is undone by a compensating step (releasing
  the stock), not by a rollback.
- **Exchanges and queues.** order-service publishes to the `order.events` topic exchange; product-catalog-service
  consumes it on `catalog-service.order.queue` (binding `order.#`) and publishes to `catalog.events`, which
  order-service consumes on `order-service.catalog.queue` (binding `catalog.#`). Both are quorum queues with a
  dead-letter queue. Each side ignores event types it does not act on.
- **At least once, applied once.** The relays mark a row published only after the broker confirms it, so an event can
  be sent twice but never lost. Consumers skip an `eventId` already in `processed_event`, and the use cases ignore an
  order or reservation that has already moved on.
- **Failures.** A message that cannot be parsed is rejected straight to the dead-letter queue. One that fails to apply
  (e.g. unknown order) is retried, then dead-lettered for a person to inspect.
- **Stock is all or nothing.** A reservation takes every line or none, from lots chosen first expiry, first out,
  skipping quarantined, expired and short-dated lots (see [Stock lots and GS1](../../README.md#stock-lots-and-gs1)).
  The lots used are stored with the reservation, so a release returns units to the same lots and a recall can find
  the order.
- **Who may do what.** Only a Medical Sales Rep (token `msr_id`) places orders, always for themselves. Only the AI
  Agent Service (`ROLE_AGENT`) approves or rejects, and the approver is the token's principal, never the body.
  Delivery is confirmed by the order's own MSR or the agent.
- **Transport conditions (GDP).** Each confirmed line carries the product's storage conditions. Confirming delivery
  of an order with such lines needs the receiver's transport report; a break quarantines the delivery until Quality
  releases or rejects it. A rejected delivery still fulfils the reservation: the units left stock either way.
- **Timing.** The MSR sees `AWAITING_STOCK` in the `202` response and polls `GET /api/v1/orders/{id}` until the
  catalog's answer arrives, usually within a couple of relay intervals.

Code: [CreateOrderUseCase](../../order-service/order-domain/src/main/java/com/das/cleanddd/domain/order/usecases/services/CreateOrderUseCase.java) ·
[OrderOutboxRelay](../../order-service/order-infra/src/main/java/com/das/infra/service/order/outbox/OrderOutboxRelay.java) ·
[catalog OrderEventHandler](../../product-catalog-service/catalog-infra/src/main/java/com/das/infra/service/catalog/events/OrderEventHandler.java) ·
[ReserveStockForOrderUseCase](../../product-catalog-service/catalog-domain/src/main/java/com/das/cleanddd/domain/catalog/reservation/usecases/ReserveStockForOrderUseCase.java) ·
[order CatalogEventHandler](../../order-service/order-infra/src/main/java/com/das/infra/service/order/events/CatalogEventHandler.java)

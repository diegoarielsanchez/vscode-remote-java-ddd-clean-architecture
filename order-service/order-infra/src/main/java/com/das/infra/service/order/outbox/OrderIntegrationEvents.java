package com.das.infra.service.order.outbox;

import java.math.BigDecimal;

import com.das.cleanddd.domain.order.events.OrderApprovedEvent;
import com.das.cleanddd.domain.order.events.OrderCreatedEvent;
import com.das.cleanddd.domain.order.events.OrderDeliveredEvent;
import com.das.cleanddd.domain.order.events.OrderDomainEvent;
import com.das.cleanddd.domain.order.events.OrderRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderSubmittedForApprovalEvent;

/**
 * Maps private domain events to the public {@code order.events} contract (schema version 1).
 * Routing keys are unchanged from the previous publisher, so existing bindings keep working.
 */
public final class OrderIntegrationEvents {

    public static final String AGGREGATE_TYPE = "order";
    public static final int SCHEMA_VERSION = 1;

    /**
     * Event payload ({@code data}); null fields do not apply to the event type.
     * {@code decidedBy} is the approver/rejecter recorded on the order.
     */
    public record OrderEventData(String medicalSalesRepId, Integer lineCount, BigDecimal totalAmount,
                                 String decidedBy, String reason) {}

    public record Mapped(String eventType, String aggregateId, OrderEventData data) {}

    private OrderIntegrationEvents() {}

    public static Mapped map(OrderDomainEvent event) {
        return switch (event) {
            case OrderCreatedEvent e -> new Mapped("order.created", e.id(),
                    new OrderEventData(e.medicalSalesRepId(), e.lineCount(), e.totalAmount(), null, null));
            case OrderSubmittedForApprovalEvent e -> new Mapped("order.submitted-for-approval", e.id(),
                    new OrderEventData(null, null, null, null, null));
            case OrderApprovedEvent e -> new Mapped("order.approved", e.id(),
                    new OrderEventData(null, null, null, e.approvedBy(), null));
            case OrderRejectedEvent e -> new Mapped("order.rejected", e.id(),
                    new OrderEventData(null, null, null, e.rejectedBy(), e.reason()));
            case OrderDeliveredEvent e -> new Mapped("order.delivered", e.id(),
                    new OrderEventData(null, null, null, null, null));
        };
    }
}

package com.das.infra.service.order.outbox;

import java.math.BigDecimal;
import java.util.List;

import com.das.cleanddd.domain.order.events.OrderApprovedEvent;
import com.das.cleanddd.domain.order.events.OrderCreatedEvent;
import com.das.cleanddd.domain.order.events.OrderDeliveredEvent;
import com.das.cleanddd.domain.order.events.OrderDomainEvent;
import com.das.cleanddd.domain.order.events.OrderRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderStockRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderSubmittedForApprovalEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Maps private domain events to the public {@code order.events} contract (schema version 1).
 * Routing key = event type. {@code order.created} starts the stock saga: product-catalog-service
 * reserves its {@code lines}.
 */
public final class OrderIntegrationEvents {

    public static final String AGGREGATE_TYPE = "order";
    public static final int SCHEMA_VERSION = 1;

    /**
     * Event payload ({@code data}); null fields do not apply to the event type and are left out.
     * {@code decidedBy} is the approver/rejecter recorded on the order.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderEventData(String medicalSalesRepId, Integer lineCount, List<Line> lines,
                                 BigDecimal totalAmount, String decidedBy, String reason) {}

    /** A line to reserve: product and quantity only (the catalog sets the price). */
    public record Line(String productId, int quantity) {}

    public record Mapped(String eventType, String aggregateId, OrderEventData data) {}

    private OrderIntegrationEvents() {}

    public static Mapped map(OrderDomainEvent event) {
        return switch (event) {
            case OrderCreatedEvent e -> new Mapped("order.created", e.id(),
                    new OrderEventData(e.medicalSalesRepId(), e.lines().size(),
                            e.lines().stream().map(l -> new Line(l.productId(), l.quantity())).toList(),
                            null, null, null));
            case OrderSubmittedForApprovalEvent e -> new Mapped("order.submitted-for-approval", e.id(),
                    new OrderEventData(null, null, null, e.totalAmount(), null, null));
            case OrderStockRejectedEvent e -> new Mapped("order.stock-rejected", e.id(),
                    new OrderEventData(null, null, null, null, null, e.reason()));
            case OrderApprovedEvent e -> new Mapped("order.approved", e.id(),
                    new OrderEventData(null, null, null, null, e.approvedBy(), null));
            case OrderRejectedEvent e -> new Mapped("order.rejected", e.id(),
                    new OrderEventData(null, null, null, null, e.rejectedBy(), e.reason()));
            case OrderDeliveredEvent e -> new Mapped("order.delivered", e.id(),
                    new OrderEventData(null, null, null, null, null, null));
        };
    }
}

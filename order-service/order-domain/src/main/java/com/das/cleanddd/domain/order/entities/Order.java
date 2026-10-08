package com.das.cleanddd.domain.order.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.das.cleanddd.domain.order.events.OrderApprovedEvent;
import com.das.cleanddd.domain.order.events.OrderCreatedEvent;
import com.das.cleanddd.domain.order.events.OrderDeliveredEvent;
import com.das.cleanddd.domain.order.events.OrderDomainEvent;
import com.das.cleanddd.domain.order.events.OrderRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderStockRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderSubmittedForApprovalEvent;
import com.das.cleanddd.domain.shared.AggregateRoot;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

/**
 * Full workflow: AWAITING_STOCK -&gt; PENDING_APPROVAL (or STOCK_REJECTED) -&gt; APPROVED/REJECTED -&gt; DELIVERED.
 * The stock step is a choreographed saga with product-catalog-service: {@link #create} records
 * {@code OrderCreatedEvent}, the catalog reserves the stock and answers, and the answer lands in
 * {@link #confirmStock} or {@link #rejectForStock}. Each transition returns a new immutable instance
 * (matches Invoice/Settlement's style).
 */
public final class Order extends AggregateRoot<OrderDomainEvent> {

    /** Keeps the saga's events (which carry every line) well inside the outbox payload limit. */
    public static final int MAX_LINES = 20;

    private final OrderId _id;
    private final MedicalSalesRepId _medicalSalesRepId;
    private final List<OrderLine> _lines;
    private final OrderStatus _status;
    private final String _approvedBy;
    private final String _rejectedBy;
    private final String _rejectionReason;
    private final Instant _createdAt;
    private final Instant _approvedAt;
    private final Instant _rejectedAt;
    private final Instant _deliveredAt;

    public Order(OrderId id, MedicalSalesRepId medicalSalesRepId, List<OrderLine> lines, OrderStatus status,
                 String approvedBy, String rejectedBy, String rejectionReason,
                 Instant createdAt, Instant approvedAt, Instant rejectedAt, Instant deliveredAt)
            throws BusinessValidationException {
        if (medicalSalesRepId == null) {
            throw new BusinessValidationException("Medical Sales Representative is required.");
        }
        if (lines == null || lines.isEmpty()) {
            throw new BusinessValidationException("An order must have at least one line.");
        }
        if (lines.size() > MAX_LINES) {
            throw new BusinessValidationException("An order can have at most " + MAX_LINES + " lines.");
        }
        this._id = id == null ? OrderId.random() : id;
        this._medicalSalesRepId = medicalSalesRepId;
        this._lines = new ArrayList<>(lines);
        this._status = status == null ? OrderStatus.AWAITING_STOCK : status;
        this._approvedBy = approvedBy;
        this._rejectedBy = rejectedBy;
        this._rejectionReason = rejectionReason;
        this._createdAt = createdAt == null ? Instant.now() : createdAt;
        this._approvedAt = approvedAt;
        this._rejectedAt = rejectedAt;
        this._deliveredAt = deliveredAt;
    }

    /** A new order, waiting for product-catalog-service to reserve its stock. */
    public static Order create(MedicalSalesRepId medicalSalesRepId, List<OrderLine> lines) throws BusinessValidationException {
        Order order = new Order(null, medicalSalesRepId, lines, OrderStatus.AWAITING_STOCK,
                null, null, null, Instant.now(), null, null, null);
        order.record(new OrderCreatedEvent(order.id().value(), medicalSalesRepId.value(),
                order._lines.stream()
                        .map(l -> new OrderCreatedEvent.Line(l.productId().value(), l.quantity().value()))
                        .toList()));
        return order;
    }

    /**
     * The catalog reserved every line: price each line from the reservation and wait for approval.
     * Every product on the order must have a price.
     */
    public Order confirmStock(List<ReservedLinePrice> prices) throws BusinessValidationException {
        if (this._status != OrderStatus.AWAITING_STOCK) {
            throw new BusinessValidationException("Only orders AWAITING_STOCK can have their stock confirmed.");
        }
        if (prices == null) {
            throw new BusinessValidationException("Reserved prices are required.");
        }
        Map<String, ReservedLinePrice> byProduct = prices.stream()
                .collect(Collectors.toMap(p -> p.productId().value(), Function.identity(), (a, b) -> a));
        List<OrderLine> priced = new ArrayList<>();
        for (OrderLine line : this._lines) {
            ReservedLinePrice price = byProduct.get(line.productId().value());
            if (price == null || price.unitPrice() == null) {
                throw new BusinessValidationException("No reserved price for product " + line.productId().value() + ".");
            }
            priced.add(line.priced(price.productName(), price.unitPrice()));
        }
        Order updated = new Order(this._id, this._medicalSalesRepId, priced, OrderStatus.PENDING_APPROVAL,
                this._approvedBy, this._rejectedBy, this._rejectionReason,
                this._createdAt, this._approvedAt, this._rejectedAt, this._deliveredAt);
        updated.carryOverEventsFrom(this);
        updated.record(new OrderSubmittedForApprovalEvent(updated.id().value(), updated.totalAmount()));
        return updated;
    }

    /** The catalog could not reserve every line; the order ends here. */
    public Order rejectForStock(String reason) throws BusinessValidationException {
        if (this._status != OrderStatus.AWAITING_STOCK) {
            throw new BusinessValidationException("Only orders AWAITING_STOCK can be rejected for stock.");
        }
        Order updated = new Order(this._id, this._medicalSalesRepId, this._lines, OrderStatus.STOCK_REJECTED,
                this._approvedBy, this._rejectedBy, reason,
                this._createdAt, this._approvedAt, Instant.now(), this._deliveredAt);
        updated.carryOverEventsFrom(this);
        updated.record(new OrderStockRejectedEvent(updated.id().value(), reason));
        return updated;
    }

    public Order approve(String approvedBy) throws BusinessValidationException {
        if (this._status != OrderStatus.PENDING_APPROVAL) {
            throw new BusinessValidationException("Only orders PENDING_APPROVAL can be approved.");
        }
        Order updated = new Order(this._id, this._medicalSalesRepId, this._lines, OrderStatus.APPROVED,
                approvedBy, this._rejectedBy, this._rejectionReason,
                this._createdAt, Instant.now(), this._rejectedAt, this._deliveredAt);
        updated.carryOverEventsFrom(this);
        updated.record(new OrderApprovedEvent(updated.id().value(), approvedBy));
        return updated;
    }

    public Order reject(String rejectedBy, String reason) throws BusinessValidationException {
        if (this._status != OrderStatus.PENDING_APPROVAL) {
            throw new BusinessValidationException("Only orders PENDING_APPROVAL can be rejected.");
        }
        Order updated = new Order(this._id, this._medicalSalesRepId, this._lines, OrderStatus.REJECTED,
                this._approvedBy, rejectedBy, reason,
                this._createdAt, this._approvedAt, Instant.now(), this._deliveredAt);
        updated.carryOverEventsFrom(this);
        updated.record(new OrderRejectedEvent(updated.id().value(), rejectedBy, reason));
        return updated;
    }

    /** Idempotent: calling this again on an already-DELIVERED order is a no-op (no event, same instance semantics). */
    public Order markDelivered(Instant deliveredAt) throws BusinessValidationException {
        if (this._status == OrderStatus.DELIVERED) {
            return this;
        }
        if (this._status != OrderStatus.APPROVED) {
            throw new BusinessValidationException("Only APPROVED orders can be marked as delivered.");
        }
        Order updated = new Order(this._id, this._medicalSalesRepId, this._lines, OrderStatus.DELIVERED,
                this._approvedBy, this._rejectedBy, this._rejectionReason,
                this._createdAt, this._approvedAt, this._rejectedAt, deliveredAt == null ? Instant.now() : deliveredAt);
        updated.carryOverEventsFrom(this);
        updated.record(new OrderDeliveredEvent(updated.id().value()));
        return updated;
    }

    /** {@code null} until the stock is confirmed and every line is priced. */
    public BigDecimal totalAmount() {
        if (!_lines.stream().allMatch(OrderLine::isPriced)) {
            return null;
        }
        return _lines.stream()
                .map(OrderLine::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void validate() throws BusinessValidationException {
        if (this._id == null) throw new BusinessValidationException("Order id is required.");
        if (this._medicalSalesRepId == null) throw new BusinessValidationException("Medical Sales Representative is required.");
        if (this._lines == null || this._lines.isEmpty()) throw new BusinessValidationException("An order must have at least one line.");
        if (this._status == null) throw new BusinessValidationException("Order status is required.");
    }

    public OrderId id() { return _id; }
    public MedicalSalesRepId medicalSalesRepId() { return _medicalSalesRepId; }
    public List<OrderLine> lines() { return Collections.unmodifiableList(_lines); }
    public OrderStatus status() { return _status; }
    public String approvedBy() { return _approvedBy; }
    public String rejectedBy() { return _rejectedBy; }
    public String rejectionReason() { return _rejectionReason; }
    public Instant createdAt() { return _createdAt; }
    public Instant approvedAt() { return _approvedAt; }
    public Instant rejectedAt() { return _rejectedAt; }
    public Instant deliveredAt() { return _deliveredAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order other)) return false;
        return Objects.equals(_id, other._id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_id);
    }
}

package com.das.cleanddd.domain.order.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.das.cleanddd.domain.order.events.OrderApprovedEvent;
import com.das.cleanddd.domain.order.events.OrderCreatedEvent;
import com.das.cleanddd.domain.order.events.OrderDeliveredEvent;
import com.das.cleanddd.domain.order.events.OrderDomainEvent;
import com.das.cleanddd.domain.order.events.OrderRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderSubmittedForApprovalEvent;
import com.das.cleanddd.domain.shared.AggregateRoot;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

/**
 * Full approval workflow: CREATED -&gt; PENDING_APPROVAL -&gt; APPROVED/REJECTED -&gt; DELIVERED.
 * Each transition returns a new immutable instance (matches Invoice/Settlement's style).
 */
public final class Order extends AggregateRoot<OrderDomainEvent> {

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
        this._id = id == null ? OrderId.random() : id;
        this._medicalSalesRepId = medicalSalesRepId;
        this._lines = new ArrayList<>(lines);
        this._status = status == null ? OrderStatus.CREATED : status;
        this._approvedBy = approvedBy;
        this._rejectedBy = rejectedBy;
        this._rejectionReason = rejectionReason;
        this._createdAt = createdAt == null ? Instant.now() : createdAt;
        this._approvedAt = approvedAt;
        this._rejectedAt = rejectedAt;
        this._deliveredAt = deliveredAt;
    }

    public static Order create(MedicalSalesRepId medicalSalesRepId, List<OrderLine> lines) throws BusinessValidationException {
        Order order = new Order(null, medicalSalesRepId, lines, OrderStatus.CREATED,
                null, null, null, Instant.now(), null, null, null);
        order.record(new OrderCreatedEvent(order.id().value(), medicalSalesRepId.value(), lines.size(), order.totalAmount()));
        return order;
    }

    public Order submitForApproval() throws BusinessValidationException {
        if (this._status != OrderStatus.CREATED) {
            throw new BusinessValidationException("Only CREATED orders can be submitted for approval.");
        }
        Order updated = new Order(this._id, this._medicalSalesRepId, this._lines, OrderStatus.PENDING_APPROVAL,
                this._approvedBy, this._rejectedBy, this._rejectionReason,
                this._createdAt, this._approvedAt, this._rejectedAt, this._deliveredAt);
        updated.record(new OrderSubmittedForApprovalEvent(updated.id().value()));
        return updated;
    }

    public Order approve(String approvedBy) throws BusinessValidationException {
        if (this._status != OrderStatus.PENDING_APPROVAL) {
            throw new BusinessValidationException("Only orders PENDING_APPROVAL can be approved.");
        }
        Order updated = new Order(this._id, this._medicalSalesRepId, this._lines, OrderStatus.APPROVED,
                approvedBy, this._rejectedBy, this._rejectionReason,
                this._createdAt, Instant.now(), this._rejectedAt, this._deliveredAt);
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
        updated.record(new OrderDeliveredEvent(updated.id().value()));
        return updated;
    }

    public BigDecimal totalAmount() {
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

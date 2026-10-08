package com.das.cleanddd.domain.order.events;

import java.math.BigDecimal;

/** Stock is reserved and every line is priced; the order now waits for an approver. */
public record OrderSubmittedForApprovalEvent(
        String id,
        BigDecimal totalAmount) implements OrderDomainEvent {
}

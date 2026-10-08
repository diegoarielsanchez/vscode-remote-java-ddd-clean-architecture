package com.das.cleanddd.domain.order.entities;

/**
 * AWAITING_STOCK -&gt; PENDING_APPROVAL (stock reserved) or STOCK_REJECTED (not enough stock), then
 * PENDING_APPROVAL -&gt; APPROVED/REJECTED -&gt; DELIVERED. The stock step is decided asynchronously by
 * product-catalog-service (see {@link Order#confirmStock} and {@link Order#rejectForStock}).
 */
public enum OrderStatus {
    AWAITING_STOCK,
    STOCK_REJECTED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    DELIVERED
}

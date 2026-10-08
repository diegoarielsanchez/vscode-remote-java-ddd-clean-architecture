package com.das.cleanddd.domain.order.entities;

/**
 * What product-catalog-service reported for one reserved product: the name and unit price at the
 * moment the stock was reserved. Becomes the line's snapshot (see {@link OrderLineUnitPrice}).
 */
public record ReservedLinePrice(ProductId productId, String productName, OrderLineUnitPrice unitPrice) {
}

package com.das.cleanddd.domain.order.entities;

import com.das.cleanddd.domain.shared.storage.StorageCondition;

/**
 * What product-catalog-service reported for one reserved product: the name and unit price at the
 * moment the stock was reserved, and the storage conditions the units must travel under ({@code null}: none).
 * Becomes the line's snapshot (see {@link OrderLineUnitPrice}).
 */
public record ReservedLinePrice(ProductId productId, String productName, OrderLineUnitPrice unitPrice,
                                StorageCondition storageCondition) {

    public ReservedLinePrice(ProductId productId, String productName, OrderLineUnitPrice unitPrice) {
        this(productId, productName, unitPrice, null);
    }
}

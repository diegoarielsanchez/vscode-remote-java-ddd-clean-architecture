package com.das.cleanddd.domain.catalog.reservation.entities;

import java.math.BigDecimal;
import java.util.List;

import com.das.cleanddd.domain.catalog.lot.LotAllocation;
import com.das.cleanddd.domain.shared.storage.StorageCondition;

/**
 * One line of an order as the catalog handled it. {@code productName} and {@code unitPrice} are the
 * snapshot taken when the stock was reserved; both are {@code null} on a rejected reservation.
 * {@code allocations} are the lots the units came from (FEFO); empty on a rejected reservation and on
 * reservations made before stock was held in lots. {@code storageCondition} is the product's at reservation time,
 * which the units must be transported under; {@code null} when the product has none, and on a rejected reservation.
 */
public record ReservationLine(String productId, int quantity, String productName, BigDecimal unitPrice,
                              List<LotAllocation> allocations, StorageCondition storageCondition) {

    public ReservationLine {
        allocations = allocations == null ? List.of() : List.copyOf(allocations);
    }

    public ReservationLine(String productId, int quantity, String productName, BigDecimal unitPrice,
                           List<LotAllocation> allocations) {
        this(productId, quantity, productName, unitPrice, allocations, null);
    }

    public ReservationLine(String productId, int quantity, String productName, BigDecimal unitPrice) {
        this(productId, quantity, productName, unitPrice, List.of());
    }

    public static ReservationLine requested(String productId, int quantity) {
        return new ReservationLine(productId, quantity, null, null);
    }
}

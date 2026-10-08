package com.das.cleanddd.domain.catalog.reservation.entities;

import java.math.BigDecimal;

/**
 * One line of an order as the catalog handled it. {@code productName} and {@code unitPrice} are the
 * snapshot taken when the stock was reserved; both are {@code null} on a rejected reservation.
 */
public record ReservationLine(String productId, int quantity, String productName, BigDecimal unitPrice) {

    public static ReservationLine requested(String productId, int quantity) {
        return new ReservationLine(productId, quantity, null, null);
    }
}

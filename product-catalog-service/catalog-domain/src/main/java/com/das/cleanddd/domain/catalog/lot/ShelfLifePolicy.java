package com.das.cleanddd.domain.catalog.lot;

import java.time.LocalDate;

/**
 * Minimum remaining shelf life a lot must still have to be delivered (Good Distribution Practice). With
 * {@code minRemainingDays = 0} a lot can be allocated up to and including its expiration day. A lot without an
 * expiration date (stock migrated from before lots existed) is not limited.
 */
public record ShelfLifePolicy(int minRemainingDays) {

    public static final ShelfLifePolicy NONE = new ShelfLifePolicy(0);

    public ShelfLifePolicy {
        if (minRemainingDays < 0) {
            throw new IllegalArgumentException("Minimum remaining shelf life cannot be negative.");
        }
    }

    public boolean allows(ExpirationDate expirationDate, LocalDate today) {
        return expirationDate == null || !expirationDate.value().isBefore(today.plusDays(minRemainingDays));
    }
}

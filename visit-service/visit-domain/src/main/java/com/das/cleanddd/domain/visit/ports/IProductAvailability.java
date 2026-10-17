package com.das.cleanddd.domain.visit.ports;

import java.util.Optional;

/**
 * Port that asks the product catalog whether a product is active with at least a given number of
 * units on hand. Implemented in the infrastructure layer to avoid a domain-module dependency on
 * catalog-domain.
 */
public interface IProductAvailability {

    /**
     * @return whether the product is active with at least {@code quantity} units on hand, or empty
     *         when the catalog cannot be asked right now
     */
    Optional<Boolean> isAvailable(String productId, int quantity);
}

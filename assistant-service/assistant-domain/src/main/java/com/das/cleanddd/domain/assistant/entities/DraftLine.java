package com.das.cleanddd.domain.assistant.entities;

import java.util.Objects;

/**
 * A product and quantity in a draft. {@code gtin} and {@code name} are copied from the catalog when the line is
 * checked, so the rep confirms what the catalog says, not what the model wrote.
 */
public record DraftLine(String productId, Gtin gtin, String name, int quantity) {

    public DraftLine {
        Objects.requireNonNull(productId, "productId");
        Objects.requireNonNull(gtin, "gtin");
        Objects.requireNonNull(name, "name");
        if (quantity < 1) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
    }
}

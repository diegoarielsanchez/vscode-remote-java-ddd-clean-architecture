package com.das.cleanddd.domain.assistant.ports;

import java.math.BigDecimal;

/**
 * What the assistant needs to know about a catalog product. Price and exact stock are left out on purpose: the
 * model does not need them to draft an order, and availability is asked separately as yes or no.
 */
public record ProductView(String id, String name, String unit, String gtin, boolean active, boolean lotTracked,
                          Storage storage) {

    /** The product's storage conditions; {@code null} fields are not specified. */
    public record Storage(BigDecimal minTemperatureC, BigDecimal maxTemperatureC, Integer maxHumidityPercent,
                          Boolean protectFromLight, Boolean coldChain, String description) {}

    public boolean coldChain() {
        return storage != null && Boolean.TRUE.equals(storage.coldChain());
    }

    public boolean protectFromLight() {
        return storage != null && Boolean.TRUE.equals(storage.protectFromLight());
    }
}

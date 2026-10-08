package com.das.infra.service.catalog.outbox;

import java.math.BigDecimal;

import com.das.cleanddd.domain.catalog.events.ProductActivatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductCreatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductDeactivatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductDomainEvent;
import com.das.cleanddd.domain.catalog.events.ProductRestockedEvent;
import com.das.cleanddd.domain.catalog.events.ProductStockReleasedEvent;
import com.das.cleanddd.domain.catalog.events.ProductStockReservedEvent;
import com.das.cleanddd.domain.catalog.events.ProductUpdatedEvent;

/**
 * Maps private domain events to the public {@code catalog.events} contract (schema version 1).
 * Routing keys are unchanged from the previous publisher, so existing bindings keep working.
 */
public final class ProductIntegrationEvents {

    public static final String AGGREGATE_TYPE = "product";
    public static final int SCHEMA_VERSION = 1;

    /**
     * Event payload ({@code data}); null fields do not apply to the event type. Stock events carry
     * the change ({@code stockDelta}: reserved, released or added quantity) and the stock left after it.
     */
    public record ProductEventData(String name, String description, BigDecimal price, String unit, Boolean active,
                                   Integer stockDelta, Integer remainingStock) {}

    public record Mapped(String eventType, String aggregateId, ProductEventData data) {}

    private ProductIntegrationEvents() {}

    public static Mapped map(ProductDomainEvent event) {
        return switch (event) {
            case ProductCreatedEvent e -> new Mapped("catalog.product.created", e.id(),
                    new ProductEventData(e.name(), e.description(), e.price(), e.unit(), e.active(), null, null));
            case ProductUpdatedEvent e -> new Mapped("catalog.product.updated", e.id(),
                    new ProductEventData(e.name(), e.description(), e.price(), e.unit(), e.active(), null, null));
            case ProductActivatedEvent e -> new Mapped("catalog.product.activated", e.id(),
                    new ProductEventData(null, null, null, null, Boolean.TRUE, null, null));
            case ProductDeactivatedEvent e -> new Mapped("catalog.product.deactivated", e.id(),
                    new ProductEventData(null, null, null, null, Boolean.FALSE, null, null));
            case ProductStockReservedEvent e -> new Mapped("catalog.product.stock-reserved", e.id(),
                    new ProductEventData(null, null, null, null, null, e.quantityReserved(), e.remainingStock()));
            case ProductStockReleasedEvent e -> new Mapped("catalog.product.stock-released", e.id(),
                    new ProductEventData(null, null, null, null, null, e.quantityReleased(), e.remainingStock()));
            case ProductRestockedEvent e -> new Mapped("catalog.product.restocked", e.id(),
                    new ProductEventData(null, null, null, null, null, e.quantityAdded(), e.remainingStock()));
        };
    }
}

package com.das.infra.service.catalog.outbox;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.das.cleanddd.domain.catalog.events.ProductActivatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductCreatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductDeactivatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductDomainEvent;
import com.das.cleanddd.domain.catalog.events.ProductLotQuarantineLiftedEvent;
import com.das.cleanddd.domain.catalog.events.ProductLotQuarantinedEvent;
import com.das.cleanddd.domain.catalog.events.ProductRestockedEvent;
import com.das.cleanddd.domain.catalog.events.ProductStockReleasedEvent;
import com.das.cleanddd.domain.catalog.events.ProductStockReservedEvent;
import com.das.cleanddd.domain.catalog.events.ProductUpdatedEvent;

/**
 * Maps private domain events to the public {@code catalog.events} contract (schema version 1).
 * Routing keys are unchanged from the previous publisher, so existing bindings keep working.
 * {@code gtin}, {@code batchNumber}, {@code expirationDate} and {@code reason} were added later as optional fields,
 * which existing consumers ignore, so the schema version stays 1.
 */
public final class ProductIntegrationEvents {

    public static final String AGGREGATE_TYPE = "product";
    public static final int SCHEMA_VERSION = 1;

    /**
     * Event payload ({@code data}); null fields do not apply to the event type. Stock events carry
     * the change ({@code stockDelta}: reserved, released or added quantity) and the stock left after it.
     * Created and updated events carry the GTIN; a restock (goods receipt) and the lot quarantine events carry the
     * batch, and the quarantine events the {@code reason} given.
     */
    public record ProductEventData(String name, String description, BigDecimal price, String unit, Boolean active,
                                   Integer stockDelta, Integer remainingStock, String gtin, String batchNumber,
                                   LocalDate expirationDate, String reason) {

        static ProductEventData details(String name, String description, BigDecimal price, String unit, Boolean active,
                                        String gtin) {
            return new ProductEventData(name, description, price, unit, active, null, null, gtin, null, null, null);
        }

        static ProductEventData active(boolean active) {
            return new ProductEventData(null, null, null, null, active, null, null, null, null, null, null);
        }

        static ProductEventData stock(Integer delta, Integer remaining) {
            return new ProductEventData(null, null, null, null, null, delta, remaining, null, null, null, null);
        }

        static ProductEventData lot(String batchNumber, LocalDate expirationDate, Integer delta, Integer remaining,
                                    String reason) {
            return new ProductEventData(null, null, null, null, null, delta, remaining, null, batchNumber, expirationDate,
                    reason);
        }
    }

    public record Mapped(String eventType, String aggregateId, ProductEventData data) {}

    private ProductIntegrationEvents() {}

    public static Mapped map(ProductDomainEvent event) {
        return switch (event) {
            case ProductCreatedEvent e -> new Mapped("catalog.product.created", e.id(),
                    ProductEventData.details(e.name(), e.description(), e.price(), e.unit(), e.active(), e.gtin()));
            case ProductUpdatedEvent e -> new Mapped("catalog.product.updated", e.id(),
                    ProductEventData.details(e.name(), e.description(), e.price(), e.unit(), e.active(), e.gtin()));
            case ProductActivatedEvent e -> new Mapped("catalog.product.activated", e.id(), ProductEventData.active(true));
            case ProductDeactivatedEvent e -> new Mapped("catalog.product.deactivated", e.id(), ProductEventData.active(false));
            case ProductStockReservedEvent e -> new Mapped("catalog.product.stock-reserved", e.id(),
                    ProductEventData.stock(e.quantityReserved(), e.remainingStock()));
            case ProductStockReleasedEvent e -> new Mapped("catalog.product.stock-released", e.id(),
                    ProductEventData.stock(e.quantityReleased(), e.remainingStock()));
            case ProductRestockedEvent e -> new Mapped("catalog.product.restocked", e.id(),
                    ProductEventData.lot(e.batchNumber(), e.expirationDate(), e.quantityAdded(), e.remainingStock(), null));
            case ProductLotQuarantinedEvent e -> new Mapped("catalog.product.lot-quarantined", e.id(),
                    ProductEventData.lot(e.batchNumber(), null, null, null, e.reason()));
            case ProductLotQuarantineLiftedEvent e -> new Mapped("catalog.product.lot-quarantine-lifted", e.id(),
                    ProductEventData.lot(e.batchNumber(), null, null, null, e.reason()));
        };
    }
}

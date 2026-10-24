package com.das.cleanddd.domain.catalog.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.das.cleanddd.domain.catalog.events.ProductCreatedEvent;
import com.das.cleanddd.domain.catalog.events.ProductUpdatedEvent;

@DisplayName("Product traceability: batch number, serial number, expiration date")
class ProductTraceabilityTest {

    private static final LocalDate EXPIRES = LocalDate.of(2027, 3, 31);

    @ParameterizedTest
    @ValueSource(strings = {"L2026-07A", "A", "12345678901234567890", "LOT.2026/07", "sn-000123"})
    @DisplayName("accepts codes as printed on the pack, exactly as entered")
    void acceptsValidCodes(String code) {
        assertEquals(code, new ProductBatchNumber(code).value());
        assertEquals(code, new ProductSerialNumber(code).value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " L2026", "L2026 ", "LOT 7", "123456789012345678901", "L#7", "LÖT1", "L\n7"})
    @DisplayName("refuses empty, spaced, too long or unusual codes instead of altering them")
    void refusesInvalidCodes(String code) {
        assertThrows(IllegalArgumentException.class, () -> new ProductBatchNumber(code));
        assertThrows(IllegalArgumentException.class, () -> new ProductSerialNumber(code));
    }

    @Test
    @DisplayName("each field is optional")
    void eachFieldIsOptional() {
        ProductTraceability t = ProductTraceability.of(null, "SN-1", null);

        assertNull(t.batchNumber());
        assertEquals("SN-1", t.serialNumberValue());
        assertNull(t.expirationDate());
        assertEquals(ProductTraceability.NONE, ProductTraceability.of(null, null, null));
    }

    @Test
    @DisplayName("usable through the expiration day, expired from the day after; no date never expires")
    void expiresTheDayAfter() throws Exception {
        Product product = product(ProductTraceability.of(null, null, EXPIRES));

        assertFalse(product.isExpiredOn(EXPIRES.minusDays(1)));
        assertFalse(product.isExpiredOn(EXPIRES));
        assertTrue(product.isExpiredOn(EXPIRES.plusDays(1)));
        assertFalse(product(ProductTraceability.NONE).isExpiredOn(LocalDate.of(2999, 1, 1)));
    }

    @Test
    @DisplayName("update: null keeps, \"\" removes a batch or serial number, a new value replaces")
    void updateRules() {
        ProductTraceability current = ProductTraceability.of("L1", "SN-1", EXPIRES);

        assertEquals(current, current.updatedWith(null, null, null));
        ProductTraceability changed = current.updatedWith("", "SN-2", EXPIRES.plusMonths(6));
        assertNull(changed.batchNumber());
        assertEquals("SN-2", changed.serialNumberValue());
        assertEquals(EXPIRES.plusMonths(6), changed.expirationDateValue());
        assertThrows(IllegalArgumentException.class, () -> current.updatedWith(" ", null, null));
    }

    @Test
    @DisplayName("created and updated events carry the values, so the change history records them")
    void eventsCarryTheValues() throws Exception {
        Product created = Product.create(null, new ProductName("Insulin 100IU"), null,
                new ProductPrice(new BigDecimal("20.00")), new ProductUnit("VIAL"), new ProductStock(5),
                ProductTraceability.of("L2026-07A", "SN-000123", EXPIRES));
        ProductCreatedEvent createdEvent = (ProductCreatedEvent) created.pullDomainEvents().get(0);
        assertEquals("L2026-07A", createdEvent.batchNumber());
        assertEquals("SN-000123", createdEvent.serialNumber());
        assertEquals(EXPIRES, createdEvent.expirationDate());

        Product updated = created.withUpdatedDetails(created.getName(), null, created.getPrice(), created.getUnit());
        ProductUpdatedEvent updatedEvent = (ProductUpdatedEvent) updated.pullDomainEvents().get(0);
        assertEquals("L2026-07A", updatedEvent.batchNumber(), "the details-only update keeps traceability");
        assertEquals(EXPIRES, updatedEvent.expirationDate());
    }

    @Test
    @DisplayName("activating and deactivating keep the traceability")
    void activationKeepsTraceability() throws Exception {
        ProductTraceability t = ProductTraceability.of("L1", "SN-1", EXPIRES);
        Product activated = product(t).setActivate();

        assertSame(t, activated.getTraceability());
        assertSame(t, activated.setDeactivate().getTraceability());
    }

    private static Product product(ProductTraceability traceability) throws Exception {
        return new Product(ProductId.random(), new ProductName("Insulin 100IU"), null,
                new ProductPrice(new BigDecimal("20.00")), new ProductUnit("VIAL"), new ProductStock(5),
                new ProductActive(false), traceability);
    }
}

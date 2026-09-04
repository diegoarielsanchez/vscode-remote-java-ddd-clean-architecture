package com.das.cleanddd.domain.catalog.entities;

import java.math.BigDecimal;

import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Product Aggregate Root")
class ProductTest {

    private ProductName name;
    private ProductDescription description;
    private ProductPrice price;
    private ProductUnit unit;
    private ProductStock stock;

    @BeforeEach
    void setUp() throws BusinessValidationException {
        name = new ProductName("Amoxicillin 500mg");
        description = new ProductDescription("Broad-spectrum antibiotic");
        price = new ProductPrice(new BigDecimal("12.50"));
        unit = new ProductUnit("BOX");
        stock = new ProductStock(100);
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create with all fields provided")
        void shouldCreateWithAllFields() throws BusinessValidationException {
            ProductId id = ProductId.random();
            Product product = new Product(id, name, description, price, unit, stock, new ProductActive(true));

            assertEquals(id, product.getId());
            assertEquals("Amoxicillin 500mg", product.getName().value());
            assertEquals(0, price.value().compareTo(product.getPrice().value()));
            assertEquals("BOX", product.getUnit().value());
            assertEquals(100, product.getStock().value());
            assertTrue(product.isActive());
        }

        @Test
        @DisplayName("should generate a random id when id is null")
        void shouldGenerateIdWhenNull() throws BusinessValidationException {
            Product product = new Product(null, name, description, price, unit, stock, null);
            assertNotNull(product.getId());
        }

        @Test
        @DisplayName("should default active to false when active is null")
        void shouldDefaultActiveToFalse() throws BusinessValidationException {
            Product product = new Product(null, name, description, price, unit, stock, null);
            assertFalse(product.isActive());
        }

        @Test
        @DisplayName("should default stock to zero when stock is null")
        void shouldDefaultStockToZero() throws BusinessValidationException {
            Product product = new Product(null, name, description, price, unit, null, null);
            assertEquals(0, product.getStock().value());
        }

        @Test
        @DisplayName("static create() should default active to false and record ProductCreatedEvent")
        void staticCreateShouldDefaultInactiveAndRecordEvent() throws BusinessValidationException {
            Product product = Product.create(null, name, description, price, unit, stock);

            assertFalse(product.isActive());
            var events = product.pullDomainEvents();
            assertEquals(1, events.size());
            assertInstanceOf(com.das.cleanddd.domain.catalog.events.ProductCreatedEvent.class, events.get(0));
        }
    }

    @Nested
    @DisplayName("Value object invariants")
    class ValueObjectInvariants {

        @Test
        @DisplayName("ProductPrice should reject a negative value")
        void priceShouldRejectNegative() {
            assertThrows(BusinessValidationException.class, () -> new ProductPrice(new BigDecimal("-1.00")));
        }

        @Test
        @DisplayName("ProductPrice should reject a null value")
        void priceShouldRejectNull() {
            assertThrows(BusinessValidationException.class, () -> new ProductPrice(null));
        }

        @Test
        @DisplayName("ProductStock should reject a negative value")
        void stockShouldRejectNegative() {
            assertThrows(BusinessValidationException.class, () -> new ProductStock(-5));
        }

        @Test
        @DisplayName("ProductStock should reject a null value")
        void stockShouldRejectNull() {
            assertThrows(BusinessValidationException.class, () -> new ProductStock(null));
        }

        @Test
        @DisplayName("ProductName should reject an empty value")
        void nameShouldRejectEmpty() {
            assertThrows(IllegalArgumentException.class, () -> new ProductName(""));
        }

        @Test
        @DisplayName("ProductUnit should reject a blank value")
        void unitShouldRejectBlank() {
            assertThrows(IllegalArgumentException.class, () -> new ProductUnit("   "));
        }
    }

    @Nested
    @DisplayName("Activation / Deactivation")
    class ActivationDeactivation {

        @Test
        @DisplayName("setActivate() should return an active copy and record ProductActivatedEvent")
        void shouldActivate() throws BusinessValidationException {
            Product inactive = new Product(null, name, description, price, unit, stock, new ProductActive(false));
            Product activated = inactive.setActivate();

            assertTrue(activated.isActive());
            var events = activated.pullDomainEvents();
            assertEquals(1, events.size());
            assertInstanceOf(com.das.cleanddd.domain.catalog.events.ProductActivatedEvent.class, events.get(0));
        }

        @Test
        @DisplayName("setActivate() should be a no-op when already active")
        void activateShouldNoOpWhenAlreadyActive() throws BusinessValidationException {
            Product active = new Product(null, name, description, price, unit, stock, new ProductActive(true));
            Product result = active.setActivate();

            assertSame(active, result);
            assertTrue(result.pullDomainEvents().isEmpty());
        }

        @Test
        @DisplayName("setDeactivate() should return an inactive copy and record ProductDeactivatedEvent")
        void shouldDeactivate() throws BusinessValidationException {
            Product active = new Product(null, name, description, price, unit, stock, new ProductActive(true));
            Product deactivated = active.setDeactivate();

            assertFalse(deactivated.isActive());
            var events = deactivated.pullDomainEvents();
            assertEquals(1, events.size());
            assertInstanceOf(com.das.cleanddd.domain.catalog.events.ProductDeactivatedEvent.class, events.get(0));
        }

        @Test
        @DisplayName("setDeactivate() should be a no-op when already inactive")
        void deactivateShouldNoOpWhenAlreadyInactive() throws BusinessValidationException {
            Product inactive = new Product(null, name, description, price, unit, stock, new ProductActive(false));
            Product result = inactive.setDeactivate();

            assertSame(inactive, result);
            assertTrue(result.pullDomainEvents().isEmpty());
        }
    }

    @Nested
    @DisplayName("Updates")
    class Updates {

        @Test
        @DisplayName("withUpdatedDetails() should never change stock")
        void updateShouldNotTouchStock() throws BusinessValidationException {
            Product product = new Product(null, name, description, price, unit, stock, new ProductActive(true));
            ProductName newName = new ProductName("Amoxicillin 875mg");

            Product updated = product.withUpdatedDetails(newName, description, price, unit);

            assertEquals("Amoxicillin 875mg", updated.getName().value());
            assertEquals(product.getStock().value(), updated.getStock().value());
            assertEquals(1, updated.pullDomainEvents().size());
        }
    }
}

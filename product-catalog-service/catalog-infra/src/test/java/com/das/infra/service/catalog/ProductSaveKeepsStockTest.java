package com.das.infra.service.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductDescription;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductStock;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;

/**
 * Stock only changes through the atomic stock queries. Saving a product (activate, deactivate,
 * edit) must never write back the stock it read earlier: a reservation that happened in between
 * would be undone and the product oversold.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SQLProductRepository.class, ProductSnapshotLookup.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class ProductSaveKeepsStockTest {

    @Autowired private SQLProductRepository repository;
    @Autowired private ProductJpaRepository rows;

    @AfterEach
    void cleanUp() {
        rows.deleteAll();
    }

    @Test
    void savingAStaleProductDoesNotUndoAConcurrentReservation() throws Exception {
        Product created = Product.create(null, new ProductName("Amoxicillin 500mg"), new ProductDescription("Antibiotic"),
                new ProductPrice(new BigDecimal("12.50")), new ProductUnit("BOX"), new ProductStock(10));
        repository.save(created);
        assertEquals(10, rows.findById(created.getId().value()).orElseThrow().getStock(), "create still sets the stock");

        Product loaded = repository.findById(created.getId()).orElseThrow();   // admin opens the product: stock 10
        assertTrue(repository.tryReserveStock(created.getId(), 4));           // an order reserves 4 meanwhile: stock 6

        repository.save(loaded.setActivate().setDeactivate());               // admin's save, built from the stale read

        var row = rows.findById(created.getId().value()).orElseThrow();
        assertEquals(6, row.getStock(), "the reservation must survive the save");
        assertFalse(row.getActive());
    }
}

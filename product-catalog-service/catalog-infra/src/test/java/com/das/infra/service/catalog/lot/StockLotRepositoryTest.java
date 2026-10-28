package com.das.infra.service.catalog.lot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductDescription;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductStock;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;
import com.das.cleanddd.domain.catalog.lot.BatchNumber;
import com.das.cleanddd.domain.catalog.lot.ExpirationDate;
import com.das.cleanddd.domain.catalog.lot.LotStatus;
import com.das.cleanddd.domain.catalog.lot.StockLot;
import com.das.cleanddd.domain.shared.storage.StorageCondition;
import com.das.infra.service.catalog.ProductJpaRepository;
import com.das.infra.service.catalog.ProductSnapshotLookup;
import com.das.infra.service.catalog.SQLProductRepository;

/** Committed transactions (no test rollback), as in production: each call is its own unit of work. */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SQLProductRepository.class, ProductSnapshotLookup.class, SQLStockLotRepository.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class StockLotRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");
    private static final LocalDate EXPIRES = LocalDate.of(2027, 3, 31);

    @Autowired private SQLProductRepository products;
    @Autowired private SQLStockLotRepository lots;
    @Autowired private StockLotJpaRepository lotRows;
    @Autowired private ProductJpaRepository productRows;

    @AfterEach
    void cleanUp() {
        lotRows.deleteAll();
        productRows.deleteAll();
    }

    private Product product() throws Exception {
        Product p = Product.create(null, new ProductName("Insulin 100IU"), new ProductDescription("Vial"),
                new ProductPrice(new BigDecimal("20.00")), new ProductUnit("VIAL"), new ProductStock(0));
        products.save(p);
        return p;
    }

    private StockLot lot(Product p, String batch, int quantity) {
        StockLot lot = StockLot.receive(p.getId(), new BatchNumber(batch), new ExpirationDate(EXPIRES), quantity, NOW);
        lots.save(lot);
        return lot;
    }

    @Test
    void theProductsStockIsTheSumOfItsLots() throws Exception {
        Product p = product();
        lot(p, "L1", 10);
        lot(p, "L2", 5);

        assertEquals(15, products.findById(p.getId()).orElseThrow().getStock().value());
        assertEquals(15, lots.onHand(p.getId()));
        assertEquals(15, products.searchAll().get(0).getStock().value(), "lists read it in one query");
    }

    @Test
    void takeIsAtomicAndConditional() throws Exception {
        Product p = product();
        StockLot l1 = lot(p, "L1", 10);

        assertTrue(lots.tryTake(l1, 4));
        assertFalse(lots.tryTake(l1, 7), "only 6 left");
        assertEquals(6, lots.findByProductIdAndBatchNumber(p.getId(), new BatchNumber("L1")).orElseThrow().quantity());

        lots.give(p.getId(), l1.id(), 4);
        assertEquals(10, lots.onHand(p.getId()));
    }

    @Test
    void aQuarantinedLotCannotBeTaken() throws Exception {
        Product p = product();
        StockLot l1 = lot(p, "L1", 10);

        lots.save(l1.withStatus(LotStatus.QUARANTINED));

        assertFalse(lots.tryTake(l1, 1));
        assertEquals(10, lots.onHand(p.getId()), "still on hand");
    }

    @Test
    void aRejectedLotIsNoLongerOnHand() throws Exception {
        Product p = product();
        lot(p, "L1", 10);
        StockLot l2 = lot(p, "L2", 5);

        lots.save(l2.withStatus(LotStatus.REJECTED));

        assertEquals(10, lots.onHand(p.getId()));
        assertEquals(10, products.searchAll().get(0).getStock().value());
        assertEquals(5, lots.findByProductIdAndBatchNumber(p.getId(), new BatchNumber("L2")).orElseThrow().quantity(),
                "the units are kept for reconciliation");
    }

    @Test
    void aProductsStorageConditionsAreStored() throws Exception {
        StorageCondition fridge = new StorageCondition(new BigDecimal("2"), new BigDecimal("8"), 60, true);
        Product p = Product.create(null, new ProductName("Insulin 100IU"), null, new ProductPrice(new BigDecimal("20.00")),
                new ProductUnit("VIAL"), new ProductStock(0), null, true, fridge);
        products.save(p);

        assertEquals(fridge, products.findById(p.getId()).orElseThrow().getStorageCondition());
        assertEquals(fridge, products.searchAll().get(0).getStorageCondition());
        assertNull(product().getStorageCondition());
    }

    @Test
    void savingAStaleLotDoesNotUndoAConcurrentTake() throws Exception {
        Product p = product();
        StockLot loaded = lot(p, "L1", 10);              // an admin reads the lot: 10
        assertTrue(lots.tryTake(loaded, 4));              // an order takes 4 meanwhile: 6

        lots.save(loaded.withStatus(LotStatus.QUARANTINED)); // the admin's quarantine, built from the stale read

        StockLot now = lots.findByProductIdAndBatchNumber(p.getId(), new BatchNumber("L1")).orElseThrow();
        assertEquals(6, now.quantity(), "the take must survive the save");
        assertEquals(LotStatus.QUARANTINED, now.status());
    }

    @Test
    void aProductHeldWithoutBatchesKeepsItsUnbatchedLotAndFlag() throws Exception {
        Product brochure = Product.create(null, new ProductName("Patient brochure"), null,
                new ProductPrice(new BigDecimal("0.00")), new ProductUnit("UNIT"), new ProductStock(0), null, false);
        products.save(brochure);
        lots.save(StockLot.receive(brochure.getId(), StockLot.UNBATCHED, null, 100, NOW));

        Product back = products.findById(brochure.getId()).orElseThrow();
        assertFalse(back.isLotTracked());
        assertEquals(100, back.getStock().value());
        assertTrue(products.findById(product().getId()).orElseThrow().isLotTracked());
    }

    @Test
    void aBatchNumberIsUniquePerProduct() throws Exception {
        Product p = product();
        lot(p, "L1", 10);

        assertThrows(DataIntegrityViolationException.class, () -> lot(p, "L1", 5));
        lot(product(), "L1", 5); // another product may use the same batch number
    }
}

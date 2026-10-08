package com.das.infra.service.catalog.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.das.cleanddd.domain.catalog.events.ProductStockReservedEvent;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.infra.service.catalog.ProductJpaRepository;
import com.das.infra.service.catalog.SQLProductRepository;
import com.das.infra.service.catalog.SpringUnitOfWork;

/**
 * The outbox guarantee on a real database, including stock: an atomic stock reservation and its
 * {@code catalog.product.stock-reserved} event commit together, or neither does.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SQLProductRepository.class, SpringUnitOfWork.class, OutboxProductEventPublisher.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class UnitOfWorkAtomicityTest {

    @Autowired private UnitOfWork unitOfWork;
    @Autowired private SQLProductRepository repository;
    @Autowired private ProductJpaRepository productRows;
    @Autowired private IProductEventPublisher publisher;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        versions.deleteAll();
        productRows.deleteAll();
    }

    private Product savedProductWithStock(int stock) throws Exception {
        Product product = Product.create(null, new ProductName("Amoxicillin 500mg"), new ProductDescription("Antibiotic"),
                new ProductPrice(new BigDecimal("12.50")), new ProductUnit("BOX"), new ProductStock(stock));
        unitOfWork.run(() -> repository.save(product));
        return product;
    }

    private int stockOf(Product product) {
        return repository.findById(product.getId()).orElseThrow().getStock().value();
    }

    @Test
    void commitsTheStockReservationAndItsEventTogether() throws Exception {
        Product product = savedProductWithStock(10);

        unitOfWork.run(() -> {
            assertTrue(repository.tryReserveStock(product.getId(), 4));
            publisher.publish(new ProductStockReservedEvent(product.getId().value(), 4, 6));
        });

        assertEquals(6, stockOf(product));
        assertEquals(1, outbox.count());
    }

    @Test
    void rollsBackTheReservationWhenTheUnitOfWorkFails() throws Exception {
        Product product = savedProductWithStock(10);

        assertThrows(DomainException.class, () -> unitOfWork.run(() -> {
            repository.tryReserveStock(product.getId(), 4);
            publisher.publish(new ProductStockReservedEvent(product.getId().value(), 4, 6));
            throw new DomainException("failure after the reservation was recorded");
        }));

        assertEquals(10, stockOf(product), "stock must not be lost");
        assertEquals(0, outbox.count());
    }
}

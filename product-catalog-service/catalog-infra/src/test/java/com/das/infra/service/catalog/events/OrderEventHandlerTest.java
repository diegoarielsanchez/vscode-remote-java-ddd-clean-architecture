package com.das.infra.service.catalog.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.catalog.reservation.usecases.StockReservationUseCaseFactory;
import com.das.infra.service.catalog.ProductEntity;
import com.das.infra.service.catalog.ProductJpaRepository;
import com.das.infra.service.catalog.ProductSnapshotLookup;
import com.das.infra.service.catalog.SQLProductRepository;
import com.das.infra.service.catalog.SpringUnitOfWork;
import com.das.infra.service.catalog.outbox.AggregateVersionJpaRepository;
import com.das.infra.service.catalog.outbox.OutboxEventEntity;
import com.das.infra.service.catalog.outbox.OutboxEventJpaRepository;
import com.das.infra.service.catalog.outbox.OutboxProductEventPublisher;
import com.das.infra.service.catalog.outbox.OutboxStockReservationEventPublisher;
import com.das.infra.service.catalog.reservation.SQLStockReservationRepository;
import com.das.infra.service.catalog.reservation.StockReservationJpaRepository;

/**
 * The catalog's saga steps on a real database with the real use cases and repositories: stock,
 * reservation, outbox answers and the processed-event row commit together.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SQLProductRepository.class, ProductSnapshotLookup.class, SQLStockReservationRepository.class,
        SpringUnitOfWork.class, OutboxProductEventPublisher.class, OutboxStockReservationEventPublisher.class,
        StockReservationUseCaseFactory.class, OrderEventHandler.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class OrderEventHandlerTest {

    @Autowired private OrderEventHandler handler;
    @Autowired private ProductJpaRepository productRows;
    @Autowired private StockReservationJpaRepository reservations;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;
    @Autowired private ProcessedEventJpaRepository processed;

    private final String order = UUID.randomUUID().toString();

    @AfterEach
    void cleanUp() {
        processed.deleteAll();
        outbox.deleteAll();
        versions.deleteAll();
        reservations.deleteAll();
        productRows.deleteAll();
    }

    private String product(String name, int stock) {
        ProductEntity entity = new ProductEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setName(name);
        entity.setDescription("Test product");
        entity.setPrice(new BigDecimal("12.50"));
        entity.setUnit("box");
        entity.setStock(stock);
        entity.setActive(true);
        return productRows.save(entity).getId();
    }

    private int stock(String productId) {
        return productRows.findById(productId).orElseThrow().getStock();
    }

    private List<String> outboxTypes() {
        return outbox.findAll().stream().sorted(Comparator.comparing(OutboxEventEntity::getOccurredAt)
                        .thenComparing(OutboxEventEntity::getAggregateVersion))
                .map(OutboxEventEntity::getEventType).toList();
    }

    private OrderSagaEvent.Created created(OrderSagaEvent.Line... lines) {
        return new OrderSagaEvent.Created(UUID.randomUUID(), "order.created", order, List.of(lines));
    }

    @Test
    void reservesTheOrderAndAnswersConfirmed() {
        String amoxicillin = product("Amoxicillin 500mg", 10);

        assertEquals(OrderEventHandler.Outcome.APPLIED, handler.handle(created(new OrderSagaEvent.Line(amoxicillin, 4))));

        assertEquals(6, stock(amoxicillin));
        assertEquals("RESERVED", reservations.findById(order).orElseThrow().getStatus());
        assertTrue(outboxTypes().containsAll(List.of("catalog.product.stock-reserved", "catalog.reservation.confirmed")));
        assertEquals(2, outbox.count());
        assertEquals(1, processed.count());
    }

    @Test
    void aShortLineRejectsTheOrderAndHoldsNothing() {
        String amoxicillin = product("Amoxicillin 500mg", 10);
        String ibuprofen = product("Ibuprofen 400mg", 1);

        handler.handle(created(new OrderSagaEvent.Line(amoxicillin, 4), new OrderSagaEvent.Line(ibuprofen, 2)));

        assertEquals(10, stock(amoxicillin));
        assertEquals(1, stock(ibuprofen));
        assertEquals("REJECTED", reservations.findById(order).orElseThrow().getStatus());
        assertEquals(List.of("catalog.reservation.rejected"), outboxTypes());
    }

    @Test
    void anOrderRejectionReleasesTheStockOnce() {
        String amoxicillin = product("Amoxicillin 500mg", 10);
        String ibuprofen = product("Ibuprofen 400mg", 5);
        handler.handle(created(new OrderSagaEvent.Line(amoxicillin, 4), new OrderSagaEvent.Line(ibuprofen, 2)));
        outbox.deleteAll();

        handler.handle(new OrderSagaEvent.Rejected(UUID.randomUUID(), "order.rejected", order));
        handler.handle(new OrderSagaEvent.Rejected(UUID.randomUUID(), "order.rejected", order));

        assertEquals(10, stock(amoxicillin));
        assertEquals(5, stock(ibuprofen));
        assertEquals("RELEASED", reservations.findById(order).orElseThrow().getStatus());
        // Every row written between the stock updates survives (the bulk updates flush before they clear).
        assertEquals(List.of("catalog.product.stock-released", "catalog.product.stock-released",
                "catalog.reservation.released"), outboxTypes());
        assertEquals(3, processed.count());
    }

    @Test
    void deliveryFulfilsTheReservation() {
        String amoxicillin = product("Amoxicillin 500mg", 10);
        handler.handle(created(new OrderSagaEvent.Line(amoxicillin, 4)));

        handler.handle(new OrderSagaEvent.Delivered(UUID.randomUUID(), "order.delivered", order));

        assertEquals("FULFILLED", reservations.findById(order).orElseThrow().getStatus());
        assertEquals(6, stock(amoxicillin));
    }

    @Test
    void aRedeliveredEventIsSkipped() {
        String amoxicillin = product("Amoxicillin 500mg", 10);
        OrderSagaEvent.Created event = created(new OrderSagaEvent.Line(amoxicillin, 4));
        handler.handle(event);

        assertEquals(OrderEventHandler.Outcome.DUPLICATE, handler.handle(event));
        assertEquals(6, stock(amoxicillin));
    }
}

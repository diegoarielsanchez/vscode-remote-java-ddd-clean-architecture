package com.das.infra.service.order.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.order.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderLine;
import com.das.cleanddd.domain.order.entities.OrderLineQuantity;
import com.das.cleanddd.domain.order.entities.OrderStatus;
import com.das.cleanddd.domain.order.entities.ProductId;
import com.das.cleanddd.domain.order.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.order.usecases.services.OrderUseCaseFactory;
import com.das.infra.service.order.OrderJpaRepository;
import com.das.infra.service.order.SQLOrderRepository;
import com.das.infra.service.order.SpringUnitOfWork;
import com.das.infra.service.order.outbox.AggregateVersionJpaRepository;
import com.das.infra.service.order.outbox.OutboxEventEntity;
import com.das.infra.service.order.outbox.OutboxEventJpaRepository;
import com.das.infra.service.order.outbox.OutboxOrderEventPublisher;

/**
 * The catalog's answer applied on a real database with the real use cases: the order change, its
 * outbox row and the processed-event row commit together, duplicates do nothing, and a failure
 * leaves nothing behind (so the retry starts clean).
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SQLOrderRepository.class, SpringUnitOfWork.class, OutboxOrderEventPublisher.class,
        OrderUseCaseFactory.class, CatalogEventHandler.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class CatalogEventHandlerTest {

    @MockitoBean private IMedicalSalesRepValidator medicalSalesRepValidator;

    @Autowired private CatalogEventHandler handler;
    @Autowired private SQLOrderRepository repository;
    @Autowired private OrderJpaRepository orderRows;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;
    @Autowired private ProcessedEventJpaRepository processed;

    @Autowired private PlatformTransactionManager transactionManager;

    private final ProductId product = new ProductId(UUID.randomUUID().toString());

    @AfterEach
    void cleanUp() {
        processed.deleteAll();
        outbox.deleteAll();
        versions.deleteAll();
        orderRows.deleteAll();
    }

    private Order awaitingStock() throws Exception {
        Order order = Order.create(new MedicalSalesRepId(UUID.randomUUID().toString()),
                List.of(OrderLine.unpriced(product, new OrderLineQuantity(4))));
        order.pullDomainEvents();
        repository.save(order);
        return order;
    }

    private StockReservationReply.Confirmed confirmed(Order order) {
        return new StockReservationReply.Confirmed(UUID.randomUUID(), CatalogEventTranslator.CONFIRMED,
                order.id().value(), List.of(new StockReservationReply.PricedLine(product.value(), "Amoxicillin 500mg",
                        new BigDecimal("2.50"))));
    }

    /** Reads happen in a transaction in the service (unit of work / open session); same here. */
    private Order reload(Order order) {
        return new TransactionTemplate(transactionManager).execute(s -> repository.findById(order.id()).orElseThrow());
    }

    private List<String> outboxTypes() {
        return outbox.findAll().stream().sorted(Comparator.comparingLong(OutboxEventEntity::getAggregateVersion))
                .map(OutboxEventEntity::getEventType).toList();
    }

    @Test
    void aConfirmedReservationPricesTheOrderAndSubmitsItForApproval() throws Exception {
        Order order = awaitingStock();

        assertEquals(CatalogEventHandler.Outcome.APPLIED, handler.handle(confirmed(order)));

        Order pending = reload(order);
        assertEquals(OrderStatus.PENDING_APPROVAL, pending.status());
        assertEquals(0, new BigDecimal("10.00").compareTo(pending.totalAmount()));
        assertEquals(List.of("order.submitted-for-approval"), outboxTypes());
        assertEquals(1, processed.count());
    }

    @Test
    void aRejectedReservationEndsTheOrder() throws Exception {
        Order order = awaitingStock();

        handler.handle(new StockReservationReply.Rejected(UUID.randomUUID(), CatalogEventTranslator.REJECTED,
                order.id().value(), "Insufficient stock for product " + product.value()));

        Order rejected = reload(order);
        assertEquals(OrderStatus.STOCK_REJECTED, rejected.status());
        assertEquals(List.of("order.stock-rejected"), outboxTypes());
    }

    @Test
    void aRedeliveredEventIsSkipped() throws Exception {
        Order order = awaitingStock();
        StockReservationReply.Confirmed reply = confirmed(order);
        handler.handle(reply);

        assertEquals(CatalogEventHandler.Outcome.DUPLICATE, handler.handle(reply));
        assertEquals(1, outbox.count());
    }

    @Test
    void aLateAnswerForAnOrderThatMovedOnChangesNothing() throws Exception {
        Order order = awaitingStock();
        handler.handle(confirmed(order));

        handler.handle(new StockReservationReply.Rejected(UUID.randomUUID(), CatalogEventTranslator.REJECTED,
                order.id().value(), "late"));

        assertEquals(OrderStatus.PENDING_APPROVAL, reload(order).status());
        assertEquals(1, outbox.count());
        assertEquals(2, processed.count());
    }

    @Test
    void anUnknownOrderFailsAndRecordsNothing() {
        StockReservationReply.Rejected reply = new StockReservationReply.Rejected(UUID.randomUUID(),
                CatalogEventTranslator.REJECTED, UUID.randomUUID().toString(), "Insufficient stock");

        assertThrows(IllegalStateException.class, () -> handler.handle(reply));
        assertEquals(0, processed.count(), "rolled back: a retry is not mistaken for a duplicate");
    }
}

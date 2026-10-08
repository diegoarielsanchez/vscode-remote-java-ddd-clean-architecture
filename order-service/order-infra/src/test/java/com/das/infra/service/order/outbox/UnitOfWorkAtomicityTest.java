package com.das.infra.service.order.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
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

import com.das.cleanddd.domain.order.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.order.entities.Order;
import com.das.cleanddd.domain.order.entities.OrderLine;
import com.das.cleanddd.domain.order.entities.OrderLineQuantity;
import com.das.cleanddd.domain.order.entities.OrderLineUnitPrice;
import com.das.cleanddd.domain.order.entities.ProductId;
import com.das.cleanddd.domain.order.ports.IOrderEventPublisher;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.infra.service.order.OrderJpaRepository;
import com.das.infra.service.order.SQLOrderRepository;
import com.das.infra.service.order.SpringUnitOfWork;

/**
 * The outbox guarantee on a real database: the order and its outbox rows commit together, or
 * neither does. Runs without the test-managed transaction so commits and rollbacks are real.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({SQLOrderRepository.class, SpringUnitOfWork.class, OutboxOrderEventPublisher.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class UnitOfWorkAtomicityTest {

    @Autowired private UnitOfWork unitOfWork;
    @Autowired private SQLOrderRepository repository;
    @Autowired private OrderJpaRepository orderRows;
    @Autowired private IOrderEventPublisher publisher;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        versions.deleteAll();
        orderRows.deleteAll();
    }

    private static Order newSubmittedOrder() throws Exception {
        OrderLine line = new OrderLine(null, new ProductId(UUID.randomUUID().toString()), "Amoxicillin 500mg",
                new OrderLineQuantity(2), new OrderLineUnitPrice(new BigDecimal("10.00")));
        return Order.create(new MedicalSalesRepId(UUID.randomUUID().toString()), List.of(line)).submitForApproval();
    }

    @Test
    void commitsTheOrderAndBothLifecycleEventsTogether() throws Exception {
        Order order = newSubmittedOrder();

        unitOfWork.run(() -> {
            repository.save(order);
            order.pullDomainEvents().forEach(publisher::publish);
        });

        assertTrue(orderRows.findById(order.id().value()).isPresent());
        assertEquals(List.of("order.created", "order.submitted-for-approval"),
                outbox.findAll().stream().sorted(java.util.Comparator.comparingLong(OutboxEventEntity::getAggregateVersion))
                        .map(OutboxEventEntity::getEventType).toList());
    }

    @Test
    void rollsBackBothWhenTheUseCaseFailsAfterRecordingEvents() throws Exception {
        Order order = newSubmittedOrder();

        assertThrows(DomainException.class, () -> unitOfWork.run(() -> {
            repository.save(order);
            order.pullDomainEvents().forEach(publisher::publish);
            throw new DomainException("rule violated after the events were recorded");
        }));

        assertTrue(orderRows.findById(order.id().value()).isEmpty());
        assertEquals(0, outbox.count());
    }
}

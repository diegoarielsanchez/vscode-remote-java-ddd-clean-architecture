package com.das.infra.service.order.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
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
import com.das.infra.service.order.OrderRabbitMqConfig;
import com.das.infra.service.order.SQLOrderRepository;
import com.das.infra.service.order.SpringUnitOfWork;
import com.das.infra.service.order.outbox.AggregateVersionJpaRepository;
import com.das.infra.service.order.outbox.OutboxEventJpaRepository;
import com.das.infra.service.order.outbox.OutboxOrderEventPublisher;

/**
 * Order's catalog.events consumer against a real RabbitMQ: a confirmed reservation moves the order
 * to PENDING_APPROVAL, an invalid message is dead-lettered.
 * Runs against a RabbitMQ container ({@link CatalogEventFlowBrokerTest}, CI) or an existing broker
 * ({@link CatalogEventFlowExternalBrokerTest}).
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(RabbitAutoConfiguration.class)
@Import({OrderRabbitMqConfig.class, SQLOrderRepository.class, SpringUnitOfWork.class, OutboxOrderEventPublisher.class,
        OrderUseCaseFactory.class, CatalogEventTranslator.class, CatalogEventHandler.class, CatalogEventListener.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
abstract class AbstractCatalogEventFlowBrokerTest {

    @MockitoBean private IMedicalSalesRepValidator medicalSalesRepValidator;

    @Autowired private RabbitTemplate rabbit;
    @Autowired private SQLOrderRepository repository;
    @Autowired private OrderJpaRepository orderRows;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;
    @Autowired private ProcessedEventJpaRepository processed;
    @Autowired private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() {
        processed.deleteAll();
        outbox.deleteAll();
        versions.deleteAll();
        orderRows.deleteAll();
        rabbit.receive(OrderRabbitMqConfig.CATALOG_DLQ, 100);
    }

    private static Message message(String body) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setMessageId(UUID.randomUUID().toString());
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }

    /**
     * The dead-lettered copy of the message with this id, or {@code null}. Matches on the id because a
     * shared broker's dead-letter queue may hold other tests' messages.
     */
    private Message deadLettered(String messageId, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long left;
        while ((left = deadline - System.currentTimeMillis()) > 0) {
            Message dead = rabbit.receive(OrderRabbitMqConfig.CATALOG_DLQ, left);
            if (dead != null && messageId.equals(dead.getMessageProperties().getMessageId())) return dead;
        }
        return null;
    }

    private static void await(BooleanSupplier condition, String what) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) return;
            Thread.sleep(100);
        }
        throw new AssertionError("Timed out waiting for: " + what);
    }

    @Test
    void aConfirmedReservationFromTheBrokerSubmitsTheOrder() throws Exception {
        String product = UUID.randomUUID().toString();
        Order order = Order.create(new MedicalSalesRepId(UUID.randomUUID().toString()),
                List.of(OrderLine.unpriced(new ProductId(product), new OrderLineQuantity(2))));
        repository.save(order);
        UUID eventId = UUID.randomUUID();

        rabbit.send("catalog.events", "catalog.reservation.confirmed", message("""
            {"eventId":"%s","eventType":"catalog.reservation.confirmed","schemaVersion":1,"aggregateId":"%s",
             "aggregateVersion":1,"occurredAt":"2026-10-08T10:00:00Z","producer":"product-catalog-service",
             "data":{"orderId":"%s","lines":[{"productId":"%s","productName":"Amoxicillin 500mg","quantity":2,"unitPrice":12.50}]}}
            """.formatted(eventId, order.id().value(), order.id().value(), product)));

        await(() -> processed.existsById(eventId.toString()), "event processed");
        assertEquals(OrderStatus.PENDING_APPROVAL, new TransactionTemplate(transactionManager)
                .execute(s -> repository.findById(order.id()).orElseThrow().status()));
        assertEquals(1, outbox.count());
    }

    @Test
    void skipsCatalogEventsItDoesNotActOn() throws Exception {
        UUID skipped = UUID.randomUUID();
        Message skip = message("""
            {"eventId":"%s","eventType":"catalog.product.restocked","schemaVersion":1,"aggregateId":"%s",
             "aggregateVersion":3,"occurredAt":"2026-10-08T10:00:00Z","producer":"product-catalog-service",
             "data":{"stockDelta":5,"remainingStock":25}}
            """.formatted(skipped, UUID.randomUUID()));
        rabbit.send("catalog.events", "catalog.product.restocked", skip);

        assertNull(deadLettered(skip.getMessageProperties().getMessageId(), 2_000), "acknowledged, not dead-lettered");
        assertFalse(processed.existsById(skipped.toString()));
    }

    @Test
    void deadLettersAnInvalidMessage() throws Exception {
        String poison = "{\"eventType\":\"catalog.reservation.confirmed\",\"orderId\":\"x\"}";
        Message sent = message(poison);
        rabbit.send("catalog.events", "catalog.reservation.confirmed", sent);

        Message dead = deadLettered(sent.getMessageProperties().getMessageId(), 15_000);
        assertNotNull(dead, "invalid message must reach the dead-letter queue");
        assertEquals(poison, new String(dead.getBody(), StandardCharsets.UTF_8));
    }
}

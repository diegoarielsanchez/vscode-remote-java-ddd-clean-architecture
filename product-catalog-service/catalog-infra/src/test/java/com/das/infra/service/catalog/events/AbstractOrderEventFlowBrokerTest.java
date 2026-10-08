package com.das.infra.service.catalog.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.catalog.reservation.usecases.StockReservationUseCaseFactory;
import com.das.infra.service.catalog.ProductEntity;
import com.das.infra.service.catalog.ProductJpaRepository;
import com.das.infra.service.catalog.ProductRabbitMqConfig;
import com.das.infra.service.catalog.ProductSnapshotLookup;
import com.das.infra.service.catalog.SQLProductRepository;
import com.das.infra.service.catalog.SpringUnitOfWork;
import com.das.infra.service.catalog.outbox.AggregateVersionJpaRepository;
import com.das.infra.service.catalog.outbox.OutboxEventJpaRepository;
import com.das.infra.service.catalog.outbox.OutboxProductEventPublisher;
import com.das.infra.service.catalog.outbox.OutboxStockReservationEventPublisher;
import com.das.infra.service.catalog.reservation.SQLStockReservationRepository;
import com.das.infra.service.catalog.reservation.StockReservationJpaRepository;

/**
 * The catalog's order.events consumer against a real RabbitMQ: an order.created reserves stock,
 * other order events are skipped, an invalid message is dead-lettered.
 * Runs against a RabbitMQ container ({@link OrderEventFlowBrokerTest}, CI) or an existing broker
 * ({@link OrderEventFlowExternalBrokerTest}).
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(RabbitAutoConfiguration.class)
@Import({ProductRabbitMqConfig.class, SQLProductRepository.class, ProductSnapshotLookup.class,
        SQLStockReservationRepository.class, SpringUnitOfWork.class, OutboxProductEventPublisher.class,
        OutboxStockReservationEventPublisher.class, StockReservationUseCaseFactory.class,
        OrderEventTranslator.class, OrderEventHandler.class, OrderEventListener.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
abstract class AbstractOrderEventFlowBrokerTest {

    @Autowired private RabbitTemplate rabbit;
    @Autowired private ProductJpaRepository productRows;
    @Autowired private StockReservationJpaRepository reservations;
    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;
    @Autowired private ProcessedEventJpaRepository processed;

    @AfterEach
    void cleanUp() {
        processed.deleteAll();
        outbox.deleteAll();
        versions.deleteAll();
        reservations.deleteAll();
        productRows.deleteAll();
        rabbit.receive(ProductRabbitMqConfig.ORDER_DLQ, 100);
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
            Message dead = rabbit.receive(ProductRabbitMqConfig.ORDER_DLQ, left);
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

    private static String envelope(UUID eventId, String type, String orderId, String data) {
        return """
            {"eventId":"%s","eventType":"%s","schemaVersion":1,"aggregateId":"%s","aggregateVersion":1,
             "occurredAt":"2026-10-08T10:00:00Z","producer":"order-service","data":%s}
            """.formatted(eventId, type, orderId, data);
    }

    @Test
    void anOrderCreatedFromTheBrokerReservesTheStock() throws Exception {
        ProductEntity product = new ProductEntity();
        product.setId(UUID.randomUUID().toString());
        product.setName("Amoxicillin 500mg");
        product.setDescription("Antibiotic");
        product.setPrice(new BigDecimal("12.50"));
        product.setUnit("box");
        product.setStock(10);
        product.setActive(true);
        productRows.save(product);
        String order = UUID.randomUUID().toString();
        UUID eventId = UUID.randomUUID();

        rabbit.send("order.events", "order.created", message(envelope(eventId, "order.created", order,
                "{\"lines\":[{\"productId\":\"" + product.getId() + "\",\"quantity\":4}]}")));

        await(() -> processed.existsById(eventId.toString()), "event processed");
        assertEquals("RESERVED", reservations.findById(order).orElseThrow().getStatus());
        assertEquals(6, productRows.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void skipsOrderEventsItDoesNotActOn() throws Exception {
        UUID skipped = UUID.randomUUID();
        Message skip = message(envelope(skipped, "order.approved",
                UUID.randomUUID().toString(), "{\"decidedBy\":\"admin\"}"));
        rabbit.send("order.events", "order.approved", skip);

        assertNull(deadLettered(skip.getMessageProperties().getMessageId(), 2_000), "acknowledged, not dead-lettered");
        assertFalse(processed.existsById(skipped.toString()));
    }

    @Test
    void deadLettersAnInvalidMessage() throws Exception {
        String poison = "{\"eventType\":\"order.created\",\"lines\":\"all of them\"}";
        Message sent = message(poison);
        rabbit.send("order.events", "order.created", sent);

        Message dead = deadLettered(sent.getMessageProperties().getMessageId(), 15_000);
        assertNotNull(dead, "invalid message must reach the dead-letter queue");
        assertEquals(poison, new String(dead.getBody(), StandardCharsets.UTF_8));
    }
}

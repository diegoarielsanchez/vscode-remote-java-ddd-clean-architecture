package com.das.infra.service.order.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class OrderOutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private PlatformTransactionManager transactionManager;

    private RabbitTemplate rabbit;
    private OrderOutboxRelay relay;

    @BeforeEach
    void setUp() {
        rabbit = mock(RabbitTemplate.class);
        relay = new OrderOutboxRelay(outbox, rabbit, new TransactionTemplate(transactionManager),
                Clock.fixed(NOW, ZoneOffset.UTC), 50, 200, Duration.ofDays(7));
    }

    private OutboxEventEntity pending(String aggregateId, long version, String type, Instant occurredAt) {
        return outbox.save(OutboxEventEntity.pending(UUID.randomUUID(), "order", aggregateId, version, type,
                "{\"eventType\":\"" + type + "\"}", occurredAt));
    }

    private void brokerAnswers(boolean ack, boolean returned) {
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            if (returned) {
                cd.setReturned(new ReturnedMessage(inv.getArgument(2), 312, "NO_ROUTE", OrderOutboxRelay.EXCHANGE, inv.getArgument(1)));
            }
            cd.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "broker refused"));
            return null;
        }).when(rabbit).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void publishesConfirmedEventsAsRawJsonAndMarksThemPublished() {
        OutboxEventEntity first = pending("order-1", 1, "order.created", NOW.minusSeconds(10));
        OutboxEventEntity second = pending("order-1", 2, "order.deactivated", NOW.minusSeconds(5));
        brokerAnswers(true, false);

        assertEquals(2, relay.relayBatch());

        ArgumentCaptor<Message> messages = ArgumentCaptor.forClass(Message.class);
        verify(rabbit, times(2)).send(eq("order.events"), any(String.class), messages.capture(), any(CorrelationData.class));
        Message sent = messages.getAllValues().get(0);
        MessageProperties props = sent.getMessageProperties();
        assertEquals(first.getId().toString(), props.getMessageId());
        assertEquals("order.created", props.getType());
        assertEquals(MessageProperties.CONTENT_TYPE_JSON, props.getContentType());
        assertNull(props.getHeader("__TypeId__"), "no type header for consumers to trust");
        assertEquals(first.getPayload(), new String(sent.getBody(), StandardCharsets.UTF_8));

        assertEquals(NOW, outbox.findById(first.getId()).orElseThrow().getPublishedAt());
        assertEquals(NOW, outbox.findById(second.getId()).orElseThrow().getPublishedAt());
    }

    @Test
    void stopsAtTheFirstNackSoLaterEventsAreNotPublishedAhead() {
        OutboxEventEntity first = pending("order-1", 1, "order.created", NOW.minusSeconds(10));
        OutboxEventEntity second = pending("order-1", 2, "order.deactivated", NOW.minusSeconds(5));
        brokerAnswers(false, false);

        assertEquals(0, relay.relayBatch());

        verify(rabbit, times(1)).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));
        OutboxEventEntity failed = outbox.findById(first.getId()).orElseThrow();
        assertNull(failed.getPublishedAt());
        assertEquals(1, failed.getAttempts());
        assertTrue(failed.getLastError().startsWith("nack"));
        assertEquals(0, outbox.findById(second.getId()).orElseThrow().getAttempts());
    }

    @Test
    void treatsUnroutableMessagesAsFailures() {
        OutboxEventEntity event = pending("order-1", 1, "order.created", NOW.minusSeconds(10));
        brokerAnswers(true, true);

        assertEquals(0, relay.relayBatch());

        OutboxEventEntity failed = outbox.findById(event.getId()).orElseThrow();
        assertNull(failed.getPublishedAt());
        assertTrue(failed.getLastError().startsWith("unroutable"));
    }

    @Test
    void treatsAMissingConfirmAsAFailure() {
        OutboxEventEntity event = pending("order-1", 1, "order.created", NOW.minusSeconds(10));
        doNothing().when(rabbit).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        assertEquals(0, relay.relayBatch());

        OutboxEventEntity failed = outbox.findById(event.getId()).orElseThrow();
        assertEquals(1, failed.getAttempts());
        assertNotNull(failed.getLastError());
        assertTrue(failed.getLastError().startsWith("TimeoutException"));
    }

    @Test
    void cleanupRemovesOnlyPublishedEventsOlderThanTheRetention() {
        OutboxEventEntity old = pending("order-1", 1, "order.created", NOW.minus(Duration.ofDays(10)));
        old.markPublished(NOW.minus(Duration.ofDays(9)));
        OutboxEventEntity recent = pending("order-1", 2, "order.updated", NOW.minus(Duration.ofDays(1)));
        recent.markPublished(NOW.minus(Duration.ofDays(1)));
        OutboxEventEntity unpublished = pending("order-2", 1, "order.created", NOW.minus(Duration.ofDays(30)));
        outbox.flush();

        relay.scheduledCleanup();

        assertTrue(outbox.findById(old.getId()).isEmpty());
        assertTrue(outbox.findById(recent.getId()).isPresent());
        assertTrue(outbox.findById(unpublished.getId()).isPresent(), "unpublished events are never deleted");
    }
}

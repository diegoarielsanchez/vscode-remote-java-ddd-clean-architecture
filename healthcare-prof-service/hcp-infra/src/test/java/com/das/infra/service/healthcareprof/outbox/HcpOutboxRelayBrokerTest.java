package com.das.infra.service.healthcareprof.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.das.infra.service.healthcareprof.HcpRabbitMqConfig;

/**
 * The relay against a real RabbitMQ with publisher confirms and returns enabled: a confirmed event
 * is delivered and marked published; an event no queue is bound for comes back as unroutable and
 * stays in the outbox. Runs in CI; skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@ImportAutoConfiguration(RabbitAutoConfiguration.class)
@Import(HcpRabbitMqConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.rabbitmq.publisher-confirm-type=correlated",
        "spring.rabbitmq.publisher-returns=true"
})
class HcpOutboxRelayBrokerTest {

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-alpine"));

    private static final String TEST_QUEUE = "hcp-relay-broker-test";

    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private RabbitTemplate hcpRabbitTemplate;
    @Autowired private AmqpAdmin admin;
    @Autowired private TopicExchange hcpEventsExchange;

    @AfterEach
    void cleanUp() {
        outbox.deleteAll();
        admin.deleteQueue(TEST_QUEUE);
    }

    private HcpOutboxRelay relay() {
        return new HcpOutboxRelay(outbox, hcpRabbitTemplate, new TransactionTemplate(transactionManager),
                Clock.systemUTC(), 50, 5_000, Duration.ofDays(7));
    }

    private OutboxEventEntity pending(String type) {
        return outbox.save(OutboxEventEntity.pending(UUID.randomUUID(), "hcp", "8a6e0804-2bd0-4672-b79d-d97027f9071a",
                1, type, "{\"eventType\":\"" + type + "\"}", Instant.now()));
    }

    @Test
    void deliversAConfirmedEventAndMarksItPublished() {
        admin.declareExchange(hcpEventsExchange);
        Queue queue = new Queue(TEST_QUEUE, false, false, true);
        admin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue).to(hcpEventsExchange).with("hcp.#");
        admin.declareBinding(binding);
        OutboxEventEntity event = pending("hcp.created");

        assertEquals(1, relay().relayBatch());

        assertNotNull(outbox.findById(event.getId()).orElseThrow().getPublishedAt());
        Message received = hcpRabbitTemplate.receive(TEST_QUEUE, 5_000);
        assertNotNull(received);
        assertEquals(event.getId().toString(), received.getMessageProperties().getMessageId());
        assertEquals(event.getPayload(), new String(received.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void keepsAnUnroutableEventInTheOutbox() {
        admin.declareExchange(hcpEventsExchange); // no queue bound
        OutboxEventEntity event = pending("hcp.updated");

        assertEquals(0, relay().relayBatch());

        OutboxEventEntity kept = outbox.findById(event.getId()).orElseThrow();
        assertNull(kept.getPublishedAt());
        assertTrue(kept.getLastError().startsWith("unroutable"), kept.getLastError());
    }
}

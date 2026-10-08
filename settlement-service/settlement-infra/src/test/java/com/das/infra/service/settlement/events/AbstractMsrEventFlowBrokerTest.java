package com.das.infra.service.settlement.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;

import com.das.infra.service.settlement.MsrSnapshotJpaRepository;
import com.das.infra.service.settlement.SettlementRabbitMqConfig;

/**
 * Settlement's msr.events consumer against a real RabbitMQ: events update the local status
 * snapshot, invalid messages are dead-lettered.
 * Runs against a RabbitMQ container ({@link MsrEventFlowBrokerTest}, CI) or an existing broker
 * ({@link MsrEventFlowExternalBrokerTest}).
 */
@SpringBootTest(classes = AbstractMsrEventFlowBrokerTest.BrokerTestConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:settlementbroker;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
abstract class AbstractMsrEventFlowBrokerTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = MsrSnapshotJpaRepository.class) // includes the events subpackage
    @EnableJpaRepositories(basePackageClasses = MsrSnapshotJpaRepository.class)
    @Import({SettlementRabbitMqConfig.class, MsrEventTranslator.class, MsrEventHandler.class, MsrEventListener.class})
    static class BrokerTestConfig {
    }

    private static final String MSR = "5b1d6c2e-0f4a-4e7b-9c3d-2a8f6e1b7c90";

    @Autowired private RabbitTemplate rabbit;
    @Autowired private MsrSnapshotJpaRepository snapshots;
    @Autowired private ProcessedEventJpaRepository processed;

    @AfterEach
    void cleanUp() {
        processed.deleteAll();
        snapshots.deleteAll();
        rabbit.receive(SettlementRabbitMqConfig.MSR_DLQ, 100);
    }

    private static Message message(String body) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setMessageId(UUID.randomUUID().toString());
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
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
    void updatesTheSnapshotFromAPublishedEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        rabbit.send("msr.events", "msr.deactivated", message("""
            {"eventId":"%s","eventType":"msr.deactivated","schemaVersion":1,"aggregateId":"%s",
             "aggregateVersion":1,"occurredAt":"2026-10-08T10:00:00Z","producer":"medical-sales-rep-service",
             "data":{"active":false}}
            """.formatted(eventId, MSR)));

        await(() -> processed.existsById(eventId.toString()), "event processed");
        assertFalse(snapshots.findById(MSR).orElseThrow().isActive());
    }

    @Test
    void deadLettersAnInvalidMessage() throws Exception {
        String poison = "{\"eventType\":\"MSR_DEACTIVATED\",\"id\":\"" + MSR + "\"}";
        rabbit.send("msr.events", "msr.deactivated", message(poison));

        Message dead = rabbit.receive(SettlementRabbitMqConfig.MSR_DLQ, 15_000);
        assertNotNull(dead, "invalid message must reach the dead-letter queue");
        assertEquals(poison, new String(dead.getBody(), StandardCharsets.UTF_8));
    }
}

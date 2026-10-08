package com.das.infra.service.visit.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.das.cleanddd.domain.visit.usecases.services.DeactivateVisitPlanService;
import com.das.infra.service.visit.HcpSnapshotJpaRepository;
import com.das.infra.service.visit.VisitRabbitMqConfig;

/**
 * The consumer against a real RabbitMQ: topology (quorum queues, dead-letter exchange), retry and
 * dead-lettering behave as configured.
 * Runs against a RabbitMQ container ({@link HcpEventFlowBrokerTest}, CI) or an existing broker
 * ({@link HcpEventFlowExternalBrokerTest}).
 */
@SpringBootTest(classes = AbstractHcpEventFlowBrokerTest.BrokerTestConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:visitbroker;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "visit.events.retry.max-attempts=3"
})
abstract class AbstractHcpEventFlowBrokerTest {


    @Configuration
    @EnableAutoConfiguration(exclude = {RedisAutoConfiguration.class, RedisRepositoriesAutoConfiguration.class})
    @EntityScan(basePackageClasses = HcpSnapshotJpaRepository.class) // includes the events subpackage
    @EnableJpaRepositories(basePackageClasses = HcpSnapshotJpaRepository.class)
    @Import({VisitRabbitMqConfig.class, HcpEventTranslator.class, HcpEventHandler.class, HcpEventListener.class})
    static class BrokerTestConfig {
    }

    private static final String HCP = "8a6e0804-2bd0-4672-b79d-d97027f9071a";

    @Autowired private RabbitTemplate rabbit;
    @Autowired private HcpSnapshotJpaRepository snapshots;
    @Autowired private ProcessedEventJpaRepository processed;
    @MockitoBean private DeactivateVisitPlanService visitPlanDeactivation;

    @AfterEach
    void cleanUp() {
        processed.deleteAll();
        snapshots.deleteAll();
        rabbit.receive(VisitRabbitMqConfig.HCP_DLQ, 100); // drain at most one leftover
        reset(visitPlanDeactivation);
    }

    private static Message message(String body) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setMessageId(UUID.randomUUID().toString());
        return new Message(body.getBytes(StandardCharsets.UTF_8), props);
    }

    private static String deactivated(UUID eventId, long version) {
        return """
            {"eventId":"%s","eventType":"hcp.deactivated","schemaVersion":1,"aggregateId":"%s",
             "aggregateVersion":%d,"occurredAt":"2026-10-08T10:00:00Z","producer":"healthcare-prof-service",
             "data":{"active":false}}
            """.formatted(eventId, HCP, version);
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
    void appliesAPublishedEvent() throws Exception {
        UUID eventId = UUID.randomUUID();
        rabbit.send("hcp.events", "hcp.deactivated", message(deactivated(eventId, 1)));

        await(() -> processed.existsById(eventId), "event processed");
        assertFalse(snapshots.findById(HCP).orElseThrow().getActive());
        verify(visitPlanDeactivation, times(1)).deactivateByHealthCareProfId(HCP);
    }

    @Test
    void appliesARedeliveredEventOnce() throws Exception {
        UUID eventId = UUID.randomUUID();
        String body = deactivated(eventId, 1);
        rabbit.send("hcp.events", "hcp.deactivated", message(body));
        rabbit.send("hcp.events", "hcp.deactivated", message(body));
        UUID marker = UUID.randomUUID(); // a later event: once processed, both copies above were consumed
        rabbit.send("hcp.events", "hcp.activated", message(deactivated(marker, 2)
                .replace("hcp.deactivated", "hcp.activated").replace("\"active\":false", "\"active\":true")));

        await(() -> processed.existsById(marker), "marker processed");
        verify(visitPlanDeactivation, times(1)).deactivateByHealthCareProfId(HCP);
    }

    @Test
    void deadLettersAnInvalidMessageWithoutRetrying() throws Exception {
        String poison = "{\"eventType\":\"HCP_DEACTIVATED\",\"id\":\"not-a-uuid\"}";
        rabbit.send("hcp.events", "hcp.deactivated", message(poison));

        Message dead = rabbit.receive(VisitRabbitMqConfig.HCP_DLQ, 15_000);
        assertNotNull(dead, "invalid message must reach the dead-letter queue");
        assertEquals(poison, new String(dead.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void retriesATransientFailureThenDeadLetters() throws Exception {
        doThrow(new DataAccessResourceFailureException("visit plans table unavailable"))
                .when(visitPlanDeactivation).deactivateByHealthCareProfId(anyString());
        UUID eventId = UUID.randomUUID();
        rabbit.send("hcp.events", "hcp.deactivated", message(deactivated(eventId, 1)));

        Message dead = rabbit.receive(VisitRabbitMqConfig.HCP_DLQ, 20_000);
        assertNotNull(dead, "exhausted message must reach the dead-letter queue");
        verify(visitPlanDeactivation, times(3)).deactivateByHealthCareProfId(HCP);
        assertFalse(processed.existsById(eventId), "a failed attempt must not be recorded as processed");
    }
}

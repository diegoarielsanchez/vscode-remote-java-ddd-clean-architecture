package com.das.infra.service.healthcareprof.outbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.healthcareprof.events.HcpActivatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpCreatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpDeactivatedEvent;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;

@DataJpaTest
@Import(OutboxHcpEventPublisher.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class OutboxHcpEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-10-08T09:30:00Z");

    @Autowired private OutboxEventJpaRepository outbox;
    @Autowired private AggregateVersionJpaRepository versions;
    @Autowired private IHcpEventPublisher springBean;

    private OutboxHcpEventPublisher publisher;

    @BeforeEach
    void setUp() {
        EventContext context = new EventContext() {
            @Override public String correlationId() { return "trace-123"; }
            @Override public String actor() { return "admin"; }
        };
        publisher = new OutboxHcpEventPublisher(outbox, versions, context, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void appendsOneEnvelopePerEventWithIncreasingAggregateVersions() throws Exception {
        publisher.publish(new HcpCreatedEvent("hcp-1", "Ana", "Gómez", "ana@clinic.org", true));
        publisher.publish(new HcpDeactivatedEvent("hcp-1", false));
        publisher.publish(new HcpActivatedEvent("hcp-1", true));
        publisher.publish(new HcpCreatedEvent("hcp-2", "Bruno", "Díaz", "b@clinic.org", true));

        List<OutboxEventEntity> rows = outbox.findAll().stream()
                .sorted((a, b) -> a.getAggregateId().equals(b.getAggregateId())
                        ? Long.compare(a.getAggregateVersion(), b.getAggregateVersion())
                        : a.getAggregateId().compareTo(b.getAggregateId()))
                .toList();

        assertEquals(List.of("hcp.created", "hcp.deactivated", "hcp.activated", "hcp.created"),
                rows.stream().map(OutboxEventEntity::getEventType).toList());
        assertEquals(List.of(1L, 2L, 3L, 1L), rows.stream().map(OutboxEventEntity::getAggregateVersion).toList());
        rows.forEach(r -> assertNull(r.getPublishedAt()));
    }

    @Test
    void writesTheVersionedContractWithoutPersonalDataConsumersDoNotNeed() throws Exception {
        publisher.publish(new HcpCreatedEvent("hcp-1", "Ana", "Gómez", "ana@clinic.org", true));

        OutboxEventEntity row = outbox.findAll().get(0);
        JsonNode json = OutboxHcpEventPublisher.CONTRACT_JSON.readTree(row.getPayload());

        assertEquals(row.getId().toString(), json.get("eventId").asText());
        assertEquals("hcp.created", json.get("eventType").asText());
        assertEquals(1, json.get("schemaVersion").asInt());
        assertEquals("hcp-1", json.get("aggregateId").asText());
        assertEquals(1, json.get("aggregateVersion").asLong());
        assertEquals("2026-10-08T09:30:00Z", json.get("occurredAt").asText());
        assertEquals("healthcare-prof-service", json.get("producer").asText());
        assertEquals("trace-123", json.get("correlationId").asText());
        assertEquals("admin", json.get("actor").asText());
        assertEquals("Ana", json.at("/data/name").asText());
        assertEquals(true, json.at("/data/active").asBoolean());
        assertFalse(row.getPayload().contains("ana@clinic.org"), "e-mail must not leave the bounded context");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void refusesToRunOutsideAUnitOfWork() {
        // The Spring bean is proxied for @Transactional(MANDATORY): no surrounding transaction → error,
        // so an event can never be recorded without the aggregate change it belongs to.
        assertThrows(IllegalTransactionStateException.class,
                () -> springBean.publish(new HcpActivatedEvent("hcp-9", true)));
    }
}

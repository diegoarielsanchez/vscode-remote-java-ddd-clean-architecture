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
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.shared.audit.FieldChange;
import com.das.cleanddd.domain.healthcareprof.events.HcpUpdatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpActivatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpCreatedEvent;
import com.das.cleanddd.domain.healthcareprof.events.HcpDeactivatedEvent;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import tools.jackson.databind.JsonNode;

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
    @Autowired private AuditEventJpaRepository audit;
    @Autowired private IHcpEventPublisher springBean;

    private OutboxHcpEventPublisher publisher;

    @BeforeEach
    void setUp() {
        EventContext context = new EventContext() {
            @Override public String correlationId() { return "trace-123"; }
            @Override public String actor() { return "admin"; }
        };
        publisher = new OutboxHcpEventPublisher(outbox, versions, audit, context, Clock.fixed(NOW, ZoneOffset.UTC));
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

        assertEquals(row.getId().toString(), json.get("eventId").asString());
        assertEquals("hcp.created", json.get("eventType").asString());
        assertEquals(1, json.get("schemaVersion").asInt());
        assertEquals("hcp-1", json.get("aggregateId").asString());
        assertEquals(1, json.get("aggregateVersion").asLong());
        assertEquals("2026-10-08T09:30:00Z", json.get("occurredAt").asString());
        assertEquals("healthcare-prof-service", json.get("producer").asString());
        assertEquals("trace-123", json.get("correlationId").asString());
        assertEquals("admin", json.get("actor").asString());
        assertEquals("Ana", json.at("/data/name").asString());
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

    @Test
    void theAuditTrailKeepsAnEditsPreviousValuesWithoutPublishingThem() throws Exception {
        publisher.publish(new HcpUpdatedEvent("hcp-1", "Ana", "Gómez", "ana@new.example", true,
                List.of(new FieldChange("email", "ana@old.example", "ana@new.example")), "Corrected at the HCP's request"));

        OutboxEventEntity row = outbox.findAll().get(0);
        AuditEventEntity audited = audit.findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc(
                HcpIntegrationEvents.AGGREGATE_TYPE, "hcp-1").get(0);
        assertEquals("[{\"field\":\"email\",\"from\":\"ana@old.example\",\"to\":\"ana@new.example\"}]", audited.getChanges());
        assertFalse(row.getPayload().contains("ana@old.example"), "the previous value is not published");
        assertFalse(row.getPayload().contains("\"changes\""), "the changes are for the audit trail only");
        assertEquals("Corrected at the HCP's request", audited.getReason());
        assertFalse(row.getPayload().contains("Corrected at"), "the reason is for the audit trail only");
    }

    @Test
    void eventsThatAreNotEditsHaveNoChanges() {
        publisher.publish(new HcpDeactivatedEvent("hcp-1", false));

        assertNull(audit.findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc(HcpIntegrationEvents.AGGREGATE_TYPE, "hcp-1")
                .get(0).getChanges());
    }
}

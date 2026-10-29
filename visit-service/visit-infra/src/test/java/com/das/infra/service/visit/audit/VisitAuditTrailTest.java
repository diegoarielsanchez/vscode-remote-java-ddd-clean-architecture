package com.das.infra.service.visit.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.LargeFileValueObject;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitDateTime;
import com.das.cleanddd.domain.visit.entities.VisitFactory;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitItem;
import com.das.cleanddd.domain.visit.events.VisitPlanDeactivatedEvent;
import com.das.infra.service.visit.SQLVisitRepository;
import tools.jackson.databind.JsonNode;

/** Visits recorded, edited and given attachments through the real repository leave their audit trail. */
@DataJpaTest
@Import({SQLVisitRepository.class, VisitAuditTrail.class, VisitAuditTrailTest.Context.class})
class VisitAuditTrailTest {

    @TestConfiguration
    static class Context {
        @Bean
        EventContext eventContext() {
            return new EventContext() {
                @Override public String correlationId() { return "trace-42"; }
                @Override public String actor() { return "rep.ana"; }
            };
        }
    }

    private static final LocalDateTime YESTERDAY = LocalDateTime.now().minusDays(1).withHour(10).withNano(0);
    private static final String PRODUCT = "0d4c8a3e-6b1f-4f27-9e5a-3c2b1a0f9e8d";

    @Autowired private SQLVisitRepository visits;
    @Autowired private VisitAuditTrail trail;
    @Autowired private AuditEventJpaRepository audit;

    private Visit recorded() throws Exception {
        return new VisitFactory().createVisit(new VisitDateTime(YESTERDAY),
                new HealthCareProfId(UUID.randomUUID().toString()), new TextValueObject("Routine check") {},
                new Identifier(UUID.randomUUID().toString()) {}, new MedicalSalesRepId(UUID.randomUUID().toString()),
                null, List.of(new VisitItem(new Identifier(PRODUCT) {}, 2)));
    }

    private List<AuditEventEntity> trailOf(Visit visit) {
        return audit.findByAggregateTypeAndAggregateIdOrderByAggregateVersionAsc("visit", visit.visitId().value());
    }

    @Test
    void creatingEditingAndAttachingAreRecordedInOrderWithWhoAndWhen() throws Exception {
        Visit visit = recorded();
        visits.save(visit);

        Map<String, Object> before = visit.auditState();
        visit.update(visit.visitDate(), visit.healthCareProfId(), new TextValueObject("Follow-up booked") {},
                visit.visitSideId(), visit.medicalSalesRepId());
        visit.replaceItems(List.of(new VisitItem(new Identifier(PRODUCT) {}, 3)));
        visit.recordUpdatedFrom(before);
        visits.save(visit);

        visit.addProductPromoAttachment(LargeFileValueObject.ofMetadata("leaflet.png", "image/png", 1024,
                "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08"));
        visits.save(visit);

        List<AuditEventEntity> entries = trailOf(visit);
        assertEquals(List.of("visit.created", "visit.updated", "visit.attachment-added"),
                entries.stream().map(AuditEventEntity::getEventType).toList());
        assertEquals(List.of(1L, 2L, 3L), entries.stream().map(AuditEventEntity::getAggregateVersion).toList());
        entries.forEach(e -> {
            assertEquals("rep.ana", e.getActor());
            assertEquals("trace-42", e.getCorrelationId());
            assertEquals("visit-service", e.getProducer());
        });

        JsonNode created = VisitAuditTrail.AUDIT_JSON.readTree(entries.get(0).getPayload());
        assertEquals("Routine check", created.at("/data/visitComments").asString());
        assertEquals(PRODUCT + " x 2", created.at("/data/items/0").asString());
        assertNull(entries.get(0).getChanges());

        assertEquals("[{\"field\":\"visitComments\",\"from\":\"Routine check\",\"to\":\"Follow-up booked\"},"
                + "{\"field\":\"items\",\"from\":\"" + PRODUCT + " x 2\",\"to\":\"" + PRODUCT + " x 3\"}]",
                entries.get(1).getChanges());

        JsonNode attached = VisitAuditTrail.AUDIT_JSON.readTree(entries.get(2).getPayload());
        assertEquals("leaflet.png", attached.at("/data/fileName").asString());
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", attached.at("/data/sha256").asString());
    }

    @Test
    void aVisitLoadedFromTheDatabaseRecordsNothingUntilItChanges() throws Exception {
        Visit visit = recorded();
        visits.save(visit);

        Visit loaded = visits.search(new VisitId(visit.visitId().value())).orElseThrow();
        visits.save(loaded);

        assertEquals(1, trailOf(visit).size(), "saving without a change adds no entry");
    }

    @Test
    void anEditThatChangesNothingIsStillRecordedWithNoChanges() throws Exception {
        Visit visit = recorded();
        visits.save(visit);

        visit.recordUpdatedFrom(visit.auditState());
        visits.save(visit);

        assertEquals("[]", trailOf(visit).get(1).getChanges());
        assertTrue(trailOf(visit).get(1).getPayload().contains("\"visit.updated\""));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void refusesToRunOutsideATransaction() {
        assertThrows(IllegalTransactionStateException.class,
                () -> trail.record(new VisitPlanDeactivatedEvent("plan-1", "hcp x deactivated")));
    }
}

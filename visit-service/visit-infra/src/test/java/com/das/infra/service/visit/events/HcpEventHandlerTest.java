package com.das.infra.service.visit.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.das.cleanddd.domain.visit.usecases.services.DeactivateVisitPlanService;
import com.das.infra.service.visit.HcpSnapshotEntity;
import com.das.infra.service.visit.HcpSnapshotJpaRepository;
import com.das.infra.service.visit.events.HcpEventHandler.Outcome;

@DataJpaTest
@Import(HcpEventHandler.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class HcpEventHandlerTest {

    private static final String HCP = "8a6e0804-2bd0-4672-b79d-d97027f9071a";

    @Autowired private HcpEventHandler handler;
    @Autowired private HcpSnapshotJpaRepository snapshots;
    @Autowired private ProcessedEventJpaRepository processed;
    @MockitoBean private DeactivateVisitPlanService visitPlanDeactivation;

    private static HcpChange created(long version, String surname) {
        return new HcpChange(UUID.randomUUID(), HcpChange.Type.CREATED, HCP, version, "Ana", surname, true);
    }

    private static HcpChange deactivated(long version) {
        return new HcpChange(UUID.randomUUID(), HcpChange.Type.DEACTIVATED, HCP, version, null, null, false);
    }

    private static HcpChange activated(long version) {
        return new HcpChange(UUID.randomUUID(), HcpChange.Type.ACTIVATED, HCP, version, null, null, true);
    }

    private HcpSnapshotEntity snapshot() {
        return snapshots.findById(HCP).orElseThrow();
    }

    @Test
    void appliesEventsInOrderAndRecordsTheirVersion() {
        assertEquals(Outcome.APPLIED, handler.handle(created(1, "Gómez")));
        assertEquals(Outcome.APPLIED, handler.handle(deactivated(2)));

        HcpSnapshotEntity s = snapshot();
        assertEquals("Ana", s.getName());
        assertEquals("Gómez", s.getSurname());
        assertFalse(s.getActive());
        assertEquals(2L, s.getVersion());
        verify(visitPlanDeactivation, times(1)).deactivateByHealthCareProfId(HCP);
    }

    @Test
    void appliesARedeliveredEventOnlyOnce() {
        HcpChange event = deactivated(1);

        assertEquals(Outcome.APPLIED, handler.handle(event));
        assertEquals(Outcome.DUPLICATE, handler.handle(event));

        verify(visitPlanDeactivation, times(1)).deactivateByHealthCareProfId(HCP);
        assertTrue(processed.existsById(event.eventId()));
    }

    @Test
    void ignoresStaleEventsSoOlderStateNeverOverwritesNewer() {
        handler.handle(created(1, "Gómez"));
        handler.handle(activated(3));

        assertEquals(Outcome.STALE, handler.handle(deactivated(2)));

        assertTrue(snapshot().getActive());
        assertEquals(3L, snapshot().getVersion());
        verify(visitPlanDeactivation, never()).deactivateByHealthCareProfId(HCP);
    }

    @Test
    void aLateCreatedEventStillFillsInTheMissingNameWithoutRegressingState() {
        handler.handle(deactivated(2)); // arrives before "created"

        assertEquals(Outcome.STALE, handler.handle(created(1, "Gómez")));

        HcpSnapshotEntity s = snapshot();
        assertEquals("Ana", s.getName());
        assertFalse(s.getActive(), "the newer deactivation wins");
        assertEquals(2L, s.getVersion());
    }

    @Test
    void appliesToRowsCreatedByTheHttpFallbackWhichHaveNoVersion() {
        HcpSnapshotEntity fromHttp = new HcpSnapshotEntity();
        fromHttp.setId(HCP);
        fromHttp.setActive(true);
        snapshots.saveAndFlush(fromHttp);

        assertEquals(Outcome.APPLIED, handler.handle(deactivated(1)));
        assertFalse(snapshot().getActive());
    }
}

package com.das.infra.service.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.das.cleanddd.domain.settlement.entities.MedicalSalesRepId;
import com.das.infra.service.settlement.events.MsrEventHandler;
import com.das.infra.service.settlement.events.MsrEventHandler.Outcome;
import com.das.infra.service.settlement.events.MsrStatusChange;
import com.das.infra.service.settlement.events.ProcessedEventJpaRepository;

/**
 * The local MSR status snapshot: kept current by events (idempotent, ordered) and read by the
 * domain port, with msr-service consulted only for reps the snapshot has not seen.
 */
@DataJpaTest
@Import({MsrEventHandler.class, MedicalSalesRepStatusAdapter.class, MockInvoiceFileStorageConfig.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class MsrStatusFlowTest {

    private static final String MSR = "5b1d6c2e-0f4a-4e7b-9c3d-2a8f6e1b7c90";

    @Autowired private MsrEventHandler handler;
    @Autowired private MedicalSalesRepStatusAdapter port;
    @Autowired private MsrSnapshotJpaRepository snapshots;
    @Autowired private ProcessedEventJpaRepository processed;
    @MockitoBean private MedicalSalesRepHttpAdapter msrService;

    private static MsrStatusChange change(String type, long version, boolean active) {
        return new MsrStatusChange(UUID.randomUUID(), type, MSR, version, active);
    }

    private static MedicalSalesRepId msrId() {
        return new MedicalSalesRepId(MSR);
    }

    @Test
    void appliesEventsInOrderAndOnlyOnce() {
        MsrStatusChange deactivated = change("msr.deactivated", 2, false);

        assertEquals(Outcome.APPLIED, handler.handle(change("msr.created", 1, true)));
        assertEquals(Outcome.APPLIED, handler.handle(deactivated));
        assertEquals(Outcome.DUPLICATE, handler.handle(deactivated));
        assertEquals(Outcome.STALE, handler.handle(change("msr.activated", 1, true)));

        MsrSnapshotEntity s = snapshots.findById(MSR).orElseThrow();
        assertFalse(s.isActive());
        assertEquals(2L, s.getVersion());
        assertEquals(3, processed.count());
    }

    @Test
    void answersFromTheSnapshotWithoutCallingMsrService() {
        handler.handle(change("msr.created", 1, true));

        assertTrue(port.existsAndIsActive(msrId()));
        verify(msrService, never()).lookUp(any());
    }

    @Test
    void asksMsrServiceOnceForAnUnknownRepAndRemembersTheAnswer() {
        when(msrService.lookUp(any())).thenReturn(Optional.of(true));

        assertTrue(port.existsAndIsActive(msrId()));
        MsrSnapshotEntity seen = snapshots.findById(MSR).orElseThrow();
        assertTrue(seen.isActive());
        assertNull(seen.getVersion(), "HTTP answers carry no version, so the next event always wins");

        assertEquals(Outcome.APPLIED, handler.handle(change("msr.deactivated", 1, false)));
        assertFalse(port.existsAndIsActive(msrId()));
    }

    @Test
    void failsClosedAndStoresNothingWhenMsrServiceCannotAnswer() {
        when(msrService.lookUp(any())).thenReturn(Optional.empty());

        assertFalse(port.existsAndIsActive(msrId()));
        assertTrue(snapshots.findById(MSR).isEmpty(), "unknown ids must not grow the table");
    }
}

package com.das.infra.service.visit.events;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.visit.usecases.services.DeactivateVisitPlanService;
import com.das.infra.service.visit.MsrSnapshotEntity;
import com.das.infra.service.visit.MsrSnapshotJpaRepository;

/**
 * Applies {@code msr.*} events to visit-service's local MSR snapshot, in one transaction:
 * <ol>
 *   <li><b>idempotent</b> — the eventId is recorded in {@code processed_event}; a redelivered
 *       event is skipped (delivery is at-least-once);</li>
 *   <li><b>ordered</b> — events whose {@code aggregateVersion} is not newer than the snapshot's are
 *       stale and do not overwrite newer state (a late "created" may still fill in a missing name);</li>
 *   <li><b>side effects once</b> — deactivating a medical sales rep deactivates their future visit plans.</li>
 * </ol>
 * The cached active-status for the MSR is evicted so the change is visible immediately.
 */
@Service
public class MsrEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MsrEventHandler.class);

    private final ProcessedEventJpaRepository processedEvents;
    private final MsrSnapshotJpaRepository snapshots;
    private final DeactivateVisitPlanService visitPlanDeactivation;
    private final Clock clock;

    @Autowired
    public MsrEventHandler(ProcessedEventJpaRepository processedEvents, MsrSnapshotJpaRepository snapshots,
                           DeactivateVisitPlanService visitPlanDeactivation) {
        this(processedEvents, snapshots, visitPlanDeactivation, Clock.systemUTC());
    }

    MsrEventHandler(ProcessedEventJpaRepository processedEvents, MsrSnapshotJpaRepository snapshots,
                    DeactivateVisitPlanService visitPlanDeactivation, Clock clock) {
        this.processedEvents = processedEvents;
        this.snapshots = snapshots;
        this.visitPlanDeactivation = visitPlanDeactivation;
        this.clock = clock;
    }

    public enum Outcome { APPLIED, DUPLICATE, STALE }

    @Transactional
    @CacheEvict(cacheNames = "msrActiveStatus", key = "#change.msrId()")
    public Outcome handle(MsrChange change) {
        if (processedEvents.existsById(change.eventId())) {
            log.debug("Duplicate msr event skipped: eventId={}", change.eventId());
            return Outcome.DUPLICATE;
        }
        // A concurrent duplicate fails on this primary key at commit; the retry then sees it as processed.
        processedEvents.save(new ProcessedEventEntity(change.eventId(), change.type().name(), Instant.now(clock)));

        MsrSnapshotEntity snapshot = snapshots.findById(change.msrId()).orElseGet(() -> newSnapshot(change.msrId()));
        long current = snapshot.getVersion() == null ? 0 : snapshot.getVersion();

        if (change.version() <= current) {
            if (change.carriesDetails() && snapshot.getName() == null) {
                snapshot.setName(change.name());
                snapshot.setSurname(change.surname());
                snapshots.save(snapshot);
            }
            log.info("Stale msr event ignored: eventId={} type={} msrId={} version={} current={}",
                    change.eventId(), change.type(), change.msrId(), change.version(), current);
            return Outcome.STALE;
        }

        if (change.carriesDetails()) {
            snapshot.setName(change.name());
            snapshot.setSurname(change.surname());
        }
        snapshot.setActive(change.active());
        snapshot.setVersion(change.version());
        snapshots.save(snapshot);

        if (change.type() == MsrChange.Type.DEACTIVATED) {
            visitPlanDeactivation.deactivateByMedicalSalesRepId(change.msrId());
        }
        log.info("Msr event applied: eventId={} type={} msrId={} version={}",
                change.eventId(), change.type(), change.msrId(), change.version());
        return Outcome.APPLIED;
    }

    private static MsrSnapshotEntity newSnapshot(String msrId) {
        MsrSnapshotEntity entity = new MsrSnapshotEntity();
        entity.setId(msrId);
        return entity;
    }
}

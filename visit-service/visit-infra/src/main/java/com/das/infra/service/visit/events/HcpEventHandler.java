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
import com.das.infra.service.visit.HcpSnapshotEntity;
import com.das.infra.service.visit.HcpSnapshotJpaRepository;

/**
 * Applies {@code hcp.*} events to visit-service's local HCP snapshot, in one transaction:
 * <ol>
 *   <li><b>idempotent</b> — the eventId is recorded in {@code processed_event}; a redelivered
 *       event is skipped (delivery is at-least-once);</li>
 *   <li><b>ordered</b> — events whose {@code aggregateVersion} is not newer than the snapshot's are
 *       stale and do not overwrite newer state (a late "created" may still fill in a missing name);</li>
 *   <li><b>side effects once</b> — deactivating an HCP deactivates its future visit plans.</li>
 * </ol>
 * The cached active-status for the HCP is evicted so the change is visible immediately.
 */
@Service
public class HcpEventHandler {

    private static final Logger log = LoggerFactory.getLogger(HcpEventHandler.class);

    private final ProcessedEventJpaRepository processedEvents;
    private final HcpSnapshotJpaRepository snapshots;
    private final DeactivateVisitPlanService visitPlanDeactivation;
    private final Clock clock;

    @Autowired
    public HcpEventHandler(ProcessedEventJpaRepository processedEvents, HcpSnapshotJpaRepository snapshots,
                           DeactivateVisitPlanService visitPlanDeactivation) {
        this(processedEvents, snapshots, visitPlanDeactivation, Clock.systemUTC());
    }

    HcpEventHandler(ProcessedEventJpaRepository processedEvents, HcpSnapshotJpaRepository snapshots,
                    DeactivateVisitPlanService visitPlanDeactivation, Clock clock) {
        this.processedEvents = processedEvents;
        this.snapshots = snapshots;
        this.visitPlanDeactivation = visitPlanDeactivation;
        this.clock = clock;
    }

    public enum Outcome { APPLIED, DUPLICATE, STALE }

    @Transactional
    @CacheEvict(cacheNames = "hcpActiveStatus", key = "#change.hcpId()")
    public Outcome handle(HcpChange change) {
        if (processedEvents.existsById(change.eventId())) {
            log.debug("Duplicate hcp event skipped: eventId={}", change.eventId());
            return Outcome.DUPLICATE;
        }
        // A concurrent duplicate fails on this primary key at commit; the retry then sees it as processed.
        processedEvents.save(new ProcessedEventEntity(change.eventId(), change.type().name(), Instant.now(clock)));

        HcpSnapshotEntity snapshot = snapshots.findById(change.hcpId()).orElseGet(() -> newSnapshot(change.hcpId()));
        long current = snapshot.getVersion() == null ? 0 : snapshot.getVersion();

        if (change.version() <= current) {
            if (change.carriesDetails() && snapshot.getName() == null) {
                snapshot.setName(change.name());
                snapshot.setSurname(change.surname());
                snapshots.save(snapshot);
            }
            log.info("Stale hcp event ignored: eventId={} type={} hcpId={} version={} current={}",
                    change.eventId(), change.type(), change.hcpId(), change.version(), current);
            return Outcome.STALE;
        }

        if (change.carriesDetails()) {
            snapshot.setName(change.name());
            snapshot.setSurname(change.surname());
        }
        snapshot.setActive(change.active());
        snapshot.setVersion(change.version());
        snapshots.save(snapshot);

        if (change.type() == HcpChange.Type.DEACTIVATED) {
            visitPlanDeactivation.deactivateByHealthCareProfId(change.hcpId());
        }
        log.info("Hcp event applied: eventId={} type={} hcpId={} version={}",
                change.eventId(), change.type(), change.hcpId(), change.version());
        return Outcome.APPLIED;
    }

    private static HcpSnapshotEntity newSnapshot(String hcpId) {
        HcpSnapshotEntity entity = new HcpSnapshotEntity();
        entity.setId(hcpId);
        return entity;
    }
}

package com.das.infra.service.settlement.events;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.das.infra.service.settlement.MsrSnapshotEntity;
import com.das.infra.service.settlement.MsrSnapshotJpaRepository;

/**
 * Applies {@code msr.*} events to the local MSR status snapshot in one transaction:
 * idempotent (eventId recorded in {@code processed_event}) and ordered (events whose
 * {@code aggregateVersion} is not newer than the snapshot's are ignored).
 */
@Service
public class MsrEventHandler {

    private static final Logger log = LoggerFactory.getLogger(MsrEventHandler.class);

    private final ProcessedEventJpaRepository processedEvents;
    private final MsrSnapshotJpaRepository snapshots;
    private final Clock clock;

    @Autowired
    public MsrEventHandler(ProcessedEventJpaRepository processedEvents, MsrSnapshotJpaRepository snapshots) {
        this(processedEvents, snapshots, Clock.systemUTC());
    }

    MsrEventHandler(ProcessedEventJpaRepository processedEvents, MsrSnapshotJpaRepository snapshots, Clock clock) {
        this.processedEvents = processedEvents;
        this.snapshots = snapshots;
        this.clock = clock;
    }

    public enum Outcome { APPLIED, DUPLICATE, STALE }

    @Transactional
    public Outcome handle(MsrStatusChange change) {
        String eventId = change.eventId().toString();
        if (processedEvents.existsById(eventId)) {
            log.debug("Duplicate msr event skipped: eventId={}", eventId);
            return Outcome.DUPLICATE;
        }
        Instant now = Instant.now(clock);
        // A concurrent duplicate fails on this primary key at commit; the retry then sees it as processed.
        processedEvents.save(new ProcessedEventEntity(eventId, change.eventType(), now));

        MsrSnapshotEntity snapshot = snapshots.findById(change.msrId()).orElseGet(() -> new MsrSnapshotEntity(change.msrId()));
        long current = snapshot.getVersion() == null ? 0 : snapshot.getVersion();
        if (change.version() <= current) {
            log.info("Stale msr event ignored: eventId={} type={} msrId={} version={} current={}",
                    eventId, change.eventType(), change.msrId(), change.version(), current);
            return Outcome.STALE;
        }

        snapshot.update(change.active(), change.version(), now);
        snapshots.save(snapshot);
        log.info("Msr event applied: eventId={} type={} msrId={} version={} active={}",
                eventId, change.eventType(), change.msrId(), change.version(), change.active());
        return Outcome.APPLIED;
    }
}

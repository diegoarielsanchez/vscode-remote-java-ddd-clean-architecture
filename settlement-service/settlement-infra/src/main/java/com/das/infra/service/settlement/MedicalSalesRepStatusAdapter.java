package com.das.infra.service.settlement;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.das.cleanddd.domain.settlement.entities.IMedicalSalesRepPort;
import com.das.cleanddd.domain.settlement.entities.MedicalSalesRepId;

/**
 * Answers "is this medical sales rep active?" from the local snapshot kept current by
 * {@code msr.*} events, so creating a settlement does not depend on msr-service being up.
 *
 * <p>A rep the snapshot has never seen (e.g. created before settlement subscribed) is checked once
 * through {@link MedicalSalesRepHttpAdapter} and remembered; later events update that row. If both
 * the snapshot and msr-service cannot answer, the result is {@code false} (fail closed).
 */
@Primary
@Service
public class MedicalSalesRepStatusAdapter implements IMedicalSalesRepPort {

    private static final Logger log = LoggerFactory.getLogger(MedicalSalesRepStatusAdapter.class);

    private final MsrSnapshotJpaRepository snapshots;
    private final MedicalSalesRepHttpAdapter httpFallback;
    private final Clock clock;

    @Autowired
    public MedicalSalesRepStatusAdapter(MsrSnapshotJpaRepository snapshots, MedicalSalesRepHttpAdapter httpFallback) {
        this(snapshots, httpFallback, Clock.systemUTC());
    }

    MedicalSalesRepStatusAdapter(MsrSnapshotJpaRepository snapshots, MedicalSalesRepHttpAdapter httpFallback, Clock clock) {
        this.snapshots = snapshots;
        this.httpFallback = httpFallback;
        this.clock = clock;
    }

    @Override
    @Transactional
    public boolean existsAndIsActive(MedicalSalesRepId medicalSalesRepId) {
        String id = medicalSalesRepId.value();
        Optional<MsrSnapshotEntity> known = snapshots.findById(id);
        if (known.isPresent()) {
            return known.get().isActive();
        }
        Optional<Boolean> fromMsrService = httpFallback.lookUp(medicalSalesRepId);
        if (fromMsrService.isEmpty()) {
            return false;
        }
        MsrSnapshotEntity seen = new MsrSnapshotEntity(id);
        seen.update(fromMsrService.get(), null, Instant.now(clock)); // no version: the next event wins
        snapshots.save(seen);
        log.info("MSR {} not in snapshot; recorded active={} from msr-service", id, fromMsrService.get());
        return fromMsrService.get();
    }
}

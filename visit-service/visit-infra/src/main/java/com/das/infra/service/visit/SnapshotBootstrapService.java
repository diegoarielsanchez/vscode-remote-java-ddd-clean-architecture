package com.das.infra.service.visit;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.das.infra.service.visit.UpstreamDirectoryClient.HcpRecord;
import com.das.infra.service.visit.UpstreamDirectoryClient.MsrRecord;

/**
 * Best-effort snapshot seeding on cold start.
 * Reads the MSR and HCP lists from their owning services ({@link UpstreamDirectoryClient}) to pre-populate the local
 * snapshot tables. If the upstream services are unavailable or the call fails for any reason (e.g. missing JWT at
 * startup), the error is logged and the service continues. Snapshot tables are then kept current by
 * {@link com.das.infra.service.visit.events.MsrEventHandler} / {@link com.das.infra.service.visit.events.HcpEventHandler}.
 */
@Service
public class SnapshotBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotBootstrapService.class);

    private final UpstreamDirectoryClient upstream;
    private final MsrSnapshotJpaRepository msrSnapshotJpaRepo;
    private final HcpSnapshotJpaRepository hcpSnapshotJpaRepo;

    public SnapshotBootstrapService(
            UpstreamDirectoryClient upstream,
            MsrSnapshotJpaRepository msrSnapshotJpaRepo,
            HcpSnapshotJpaRepository hcpSnapshotJpaRepo) {
        this.upstream = upstream;
        this.msrSnapshotJpaRepo = msrSnapshotJpaRepo;
        this.hcpSnapshotJpaRepo = hcpSnapshotJpaRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedSnapshots() {
        seedMsr();
        seedHcp();
    }

    void seedMsr() {
        try {
            List<MsrRecord> msrList = upstream.fetchAllMedicalSalesReps();
            if (msrList.isEmpty()) {
                log.info("MSR bootstrap: no records returned (upstream empty)");
                return;
            }
            int seeded = 0;
            for (MsrRecord msr : msrList) {
                // Only seed if not already present (events may have arrived first)
                if (msr.id() != null && msrSnapshotJpaRepo.findById(msr.id()).isEmpty()) {
                    msrSnapshotJpaRepo.save(Objects.requireNonNull(toMsrSnapshotEntity(msr)));
                    seeded++;
                }
            }
            log.info("MSR snapshot bootstrap complete: {} new records seeded", seeded);
        } catch (Exception e) {
            log.warn("MSR snapshot bootstrap failed — snapshot will populate on first access. Reason: {}",
                    e.getMessage());
        }
    }

    void seedHcp() {
        try {
            List<HcpRecord> hcpList = upstream.fetchAllHealthCareProfs();
            if (hcpList.isEmpty()) {
                log.info("HCP bootstrap: no records returned (upstream empty)");
                return;
            }
            int seeded = 0;
            for (HcpRecord hcp : hcpList) {
                if (hcp.id() != null && hcpSnapshotJpaRepo.findById(hcp.id()).isEmpty()) {
                    hcpSnapshotJpaRepo.save(Objects.requireNonNull(toHcpSnapshotEntity(hcp)));
                    seeded++;
                }
            }
            log.info("HCP snapshot bootstrap complete: {} new records seeded", seeded);
        } catch (Exception e) {
            log.warn("HCP snapshot bootstrap failed — snapshot will populate on first access. Reason: {}",
                    e.getMessage());
        }
    }

    private static MsrSnapshotEntity toMsrSnapshotEntity(MsrRecord msr) {
        MsrSnapshotEntity entity = new MsrSnapshotEntity();
        entity.setId(msr.id());
        entity.setName(msr.name());
        entity.setSurname(msr.surname());
        entity.setEmail(msr.email());
        entity.setActive(msr.active());
        return entity;
    }

    private static HcpSnapshotEntity toHcpSnapshotEntity(HcpRecord hcp) {
        HcpSnapshotEntity entity = new HcpSnapshotEntity();
        entity.setId(hcp.id());
        entity.setName(hcp.name());
        entity.setSurname(hcp.surname());
        entity.setEmail(hcp.email());
        entity.setActive(hcp.active());
        if (hcp.specialties() != null) {
            String specialties = hcp.specialties().stream()
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(","));
            if (!specialties.isEmpty()) {
                entity.setSpecialties(specialties);
            }
        }
        return entity;
    }
}

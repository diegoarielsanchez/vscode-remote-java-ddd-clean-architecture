package com.das.infra.service.visit;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;

/**
 * Option B — best-effort snapshot seeding on cold start.
 * Calls the HTTP (Option A) repositories to pre-populate the local
 * snapshot tables. If the upstream services are unavailable or the
 * call fails for any reason (e.g. missing JWT at startup), the error
 * is logged and the service continues. Snapshot tables are then kept
 * current by {@link MsrSnapshotUpdater} / {@link HcpSnapshotUpdater}.
 */
@Service
public class SnapshotBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotBootstrapService.class);

    private final IMedicalSalesRepRepository httpMsrRepo;
    private final IHealthCareProfRepository httpHcpRepo;
    private final MsrSnapshotJpaRepository msrSnapshotJpaRepo;
    private final HcpSnapshotJpaRepository hcpSnapshotJpaRepo;

    public SnapshotBootstrapService(
            @Qualifier("httpMedicalSalesRepRepository") IMedicalSalesRepRepository httpMsrRepo,
            @Qualifier("httpHealthCareProfRepository") IHealthCareProfRepository httpHcpRepo,
            MsrSnapshotJpaRepository msrSnapshotJpaRepo,
            HcpSnapshotJpaRepository hcpSnapshotJpaRepo) {
        this.httpMsrRepo = httpMsrRepo;
        this.httpHcpRepo = httpHcpRepo;
        this.msrSnapshotJpaRepo = msrSnapshotJpaRepo;
        this.hcpSnapshotJpaRepo = hcpSnapshotJpaRepo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedSnapshots() {
        seedMsr();
        seedHcp();
    }

    private void seedMsr() {
        try {
            List<MedicalSalesRep> msrList = httpMsrRepo.searchAll();
            if (msrList.isEmpty()) {
                log.info("MSR bootstrap: no records returned (upstream unavailable or empty)");
                return;
            }
            int seeded = 0;
            for (MedicalSalesRep msr : msrList) {
                // Only seed if not already present (events may have arrived first)
                if (msr.getId() != null) {
                    String id = msr.getId().value();
                    if (id != null && msrSnapshotJpaRepo.findById(id).isEmpty()) {
                        msrSnapshotJpaRepo.save(Objects.requireNonNull(toMsrSnapshotEntity(msr)));
                        seeded++;
                    }
                }
            }
            log.info("MSR snapshot bootstrap complete: {} new records seeded", seeded);
        } catch (Exception e) {
            log.warn("MSR snapshot bootstrap failed — snapshot will populate on first access. Reason: {}",
                    e.getMessage());
        }
    }

    private void seedHcp() {
        try {
            List<HealthCareProf> hcpList = httpHcpRepo.searchAll();
            if (hcpList.isEmpty()) {
                log.info("HCP bootstrap: no records returned (upstream unavailable or empty)");
                return;
            }
            int seeded = 0;
            for (HealthCareProf hcp : hcpList) {
                String id = hcp.getId() != null ? hcp.getId().value() : null;
                if (id != null && hcpSnapshotJpaRepo.findById(id).isEmpty()) {
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

    private MsrSnapshotEntity toMsrSnapshotEntity(MedicalSalesRep msr) {
        MsrSnapshotEntity entity = new MsrSnapshotEntity();
        entity.setId(msr.getId().value());
        entity.setName(msr.getName() != null ? msr.getName().value() : null);
        entity.setSurname(msr.getSurname() != null ? msr.getSurname().value() : null);
        entity.setEmail(msr.getEmail() != null ? msr.getEmail().value() : null);
        entity.setActive(msr.getActive() != null ? msr.getActive().value() : null);
        return entity;
    }

    private HcpSnapshotEntity toHcpSnapshotEntity(HealthCareProf hcp) {
        HcpSnapshotEntity entity = new HcpSnapshotEntity();
        entity.setId(hcp.getId().value());
        entity.setName(hcp.getName() != null ? hcp.getName().value() : null);
        entity.setSurname(hcp.getSurname() != null ? hcp.getSurname().value() : null);
        entity.setEmail(hcp.getEmail() != null ? hcp.getEmail().value() : null);
        entity.setActive(hcp.getActive() != null ? hcp.getActive().value() : null);
        if (hcp.getSpecialties() != null && !hcp.getSpecialties().isEmpty()) {
                entity.setSpecialties(hcp.getSpecialties().stream()
                    .map(specialty -> specialty.code())
                    .collect(Collectors.joining(",")));
        }
        return entity;
    }
}

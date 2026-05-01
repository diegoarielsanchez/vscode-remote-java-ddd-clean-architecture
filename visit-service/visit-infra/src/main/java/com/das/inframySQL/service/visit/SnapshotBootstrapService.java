package com.das.inframySQL.service.visit;

import java.util.List;

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
 * is logged and the service continues — the snapshot will be populated
 * lazily on first access via the read-through fallback in
 * {@link SnapshotMsrRepository} / {@link SnapshotHcpRepository},
 * and kept current by {@link MsrSnapshotUpdater} / {@link HcpSnapshotUpdater}.
 */
@Service
public class SnapshotBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(SnapshotBootstrapService.class);

    private final IMedicalSalesRepRepository httpMsrRepo;
    private final IHealthCareProfRepository httpHcpRepo;
    private final SnapshotMsrRepository snapshotMsrRepo;
    private final SnapshotHcpRepository snapshotHcpRepo;

    public SnapshotBootstrapService(
            @Qualifier("httpMedicalSalesRepRepository") IMedicalSalesRepRepository httpMsrRepo,
            @Qualifier("httpHealthCareProfRepository") IHealthCareProfRepository httpHcpRepo,
            SnapshotMsrRepository snapshotMsrRepo,
            SnapshotHcpRepository snapshotHcpRepo) {
        this.httpMsrRepo = httpMsrRepo;
        this.httpHcpRepo = httpHcpRepo;
        this.snapshotMsrRepo = snapshotMsrRepo;
        this.snapshotHcpRepo = snapshotHcpRepo;
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
                if (snapshotMsrRepo.findById(msr.getId()).isEmpty()) {
                    snapshotMsrRepo.save(msr);
                    seeded++;
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
                if (snapshotHcpRepo.findById(hcp.getId()).isEmpty()) {
                    snapshotHcpRepo.save(hcp);
                    seeded++;
                }
            }
            log.info("HCP snapshot bootstrap complete: {} new records seeded", seeded);
        } catch (Exception e) {
            log.warn("HCP snapshot bootstrap failed — snapshot will populate on first access. Reason: {}",
                    e.getMessage());
        }
    }
}

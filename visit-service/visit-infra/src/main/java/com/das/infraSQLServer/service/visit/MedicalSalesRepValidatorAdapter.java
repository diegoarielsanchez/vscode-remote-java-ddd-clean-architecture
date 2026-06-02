package com.das.infraSQLServer.service.visit;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;

@Service
public class MedicalSalesRepValidatorAdapter implements IMedicalSalesRepValidator {

    private static final Logger log = LoggerFactory.getLogger(MedicalSalesRepValidatorAdapter.class);

    private final MsrSnapshotJpaRepository msrSnapshotJpaRepository;
    private final IMedicalSalesRepRepository httpMsrRepository;

    public MedicalSalesRepValidatorAdapter(
            MsrSnapshotJpaRepository msrSnapshotJpaRepository,
            @Qualifier("httpMedicalSalesRepRepository") IMedicalSalesRepRepository httpMsrRepository) {
        this.msrSnapshotJpaRepository = msrSnapshotJpaRepository;
        this.httpMsrRepository = httpMsrRepository;
    }

    @Override
    public boolean existsAndActive(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        // Always check the authoritative source first so stale snapshots never
        // allow an inactive MSR to be used in a new or updated VisitPlan.
        try {
            Optional<MedicalSalesRep> msr = httpMsrRepository.findById(new MedicalSalesRepId(id));
            if (msr.isEmpty()) {
                return false;
            }
            boolean active = Boolean.TRUE.equals(
                    msr.get().getActive() != null ? msr.get().getActive().value() : null);

            // Keep the local snapshot in sync with the authoritative result.
            MsrSnapshotEntity entity = msrSnapshotJpaRepository.findById(id).orElse(new MsrSnapshotEntity());
            entity.setId(id);
            entity.setName(msr.get().getName() != null ? msr.get().getName().value() : null);
            entity.setSurname(msr.get().getSurname() != null ? msr.get().getSurname().value() : null);
            entity.setEmail(msr.get().getEmail() != null ? msr.get().getEmail().value() : null);
            entity.setActive(active);
            msrSnapshotJpaRepository.save(entity);
            log.info("MSR existence check via HTTP: id={} active={}", id, active);

            return active;
        } catch (Exception e) {
            // HTTP call failed — fall back to the local snapshot to avoid blocking
            // writes when the MSR service is temporarily unavailable.
            log.warn("HTTP call for MSR id={} failed, falling back to local snapshot: {}", id, e.getMessage());
            Optional<MsrSnapshotEntity> snapshot = msrSnapshotJpaRepository.findById(id);
            if (snapshot.isPresent()) {
                return Boolean.TRUE.equals(snapshot.get().getActive());
            }
            return false;
        }
    }
}

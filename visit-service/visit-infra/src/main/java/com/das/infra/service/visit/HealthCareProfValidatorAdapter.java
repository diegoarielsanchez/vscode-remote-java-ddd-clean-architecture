package com.das.infra.service.visit;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;

@Service
public class HealthCareProfValidatorAdapter implements IHealthCareProfValidator {

    private static final Logger log = LoggerFactory.getLogger(HealthCareProfValidatorAdapter.class);

    private final HcpSnapshotJpaRepository hcpSnapshotJpaRepository;
    private final IHealthCareProfRepository httpHcpRepository;

    public HealthCareProfValidatorAdapter(
            HcpSnapshotJpaRepository hcpSnapshotJpaRepository,
            @Qualifier("httpHealthCareProfRepository") IHealthCareProfRepository httpHcpRepository) {
        this.hcpSnapshotJpaRepository = hcpSnapshotJpaRepository;
        this.httpHcpRepository = httpHcpRepository;
    }

    @Override
    public boolean existsAndActive(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }

        // Always check the authoritative source first so stale snapshots never
        // allow an inactive HCP to be used in a new or updated VisitPlan.
        try {
            Optional<HealthCareProf> hcp = httpHcpRepository.findById(new HealthCareProfId(id));
            if (hcp.isEmpty()) {
                return false;
            }
            boolean active = Boolean.TRUE.equals(
                    hcp.get().getActive() != null ? hcp.get().getActive().value() : null);

            // Keep the local snapshot in sync with the authoritative result.
            HcpSnapshotEntity entity = hcpSnapshotJpaRepository.findById(id).orElse(new HcpSnapshotEntity());
            entity.setId(id);
            entity.setName(hcp.get().getName() != null ? hcp.get().getName().value() : null);
            entity.setSurname(hcp.get().getSurname() != null ? hcp.get().getSurname().value() : null);
            entity.setEmail(hcp.get().getEmail() != null ? hcp.get().getEmail().value() : null);
            entity.setActive(active);
            hcpSnapshotJpaRepository.save(entity);
            log.info("HCP existence check via HTTP: id={} active={}", id, active);

            return active;
        } catch (Exception e) {
            // HTTP call failed — fall back to the local snapshot to avoid blocking
            // writes when the HCP service is temporarily unavailable.
            log.warn("HTTP call for HCP id={} failed, falling back to local snapshot: {}", id, e.getMessage());
            Optional<HcpSnapshotEntity> snapshot = hcpSnapshotJpaRepository.findById(id);
            if (snapshot.isPresent()) {
                return Boolean.TRUE.equals(snapshot.get().getActive());
            }
            return false;
        }
    }
}

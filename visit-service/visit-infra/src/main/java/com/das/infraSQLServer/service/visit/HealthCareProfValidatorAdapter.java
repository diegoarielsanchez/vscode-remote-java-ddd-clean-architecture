package com.das.infraSQLServer.service.visit;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;

@Service
public class HealthCareProfValidatorAdapter implements IHealthCareProfValidator {

    private final HcpSnapshotJpaRepository hcpSnapshotJpaRepository;

    public HealthCareProfValidatorAdapter(HcpSnapshotJpaRepository hcpSnapshotJpaRepository) {
        this.hcpSnapshotJpaRepository = hcpSnapshotJpaRepository;
    }

    @Override
    public boolean existsAndActive(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return hcpSnapshotJpaRepository.findById(id)
            .map(hcp -> Boolean.TRUE.equals(hcp.getActive()))
            .orElse(false);
    }
}

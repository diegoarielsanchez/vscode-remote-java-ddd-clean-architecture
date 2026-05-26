package com.das.infraSQLServer.service.visit;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;

@Service
public class MedicalSalesRepValidatorAdapter implements IMedicalSalesRepValidator {

    private final MsrSnapshotJpaRepository msrSnapshotJpaRepository;

    public MedicalSalesRepValidatorAdapter(MsrSnapshotJpaRepository msrSnapshotJpaRepository) {
        this.msrSnapshotJpaRepository = msrSnapshotJpaRepository;
    }

    @Override
    public boolean existsAndActive(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        return msrSnapshotJpaRepository.findById(id)
            .map(msr -> Boolean.TRUE.equals(msr.getActive()))
            .orElse(false);
    }
}

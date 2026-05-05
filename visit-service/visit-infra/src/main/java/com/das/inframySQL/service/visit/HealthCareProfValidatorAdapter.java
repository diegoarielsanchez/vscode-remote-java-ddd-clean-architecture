package com.das.inframySQL.service.visit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;

@Service
public class HealthCareProfValidatorAdapter implements IHealthCareProfValidator {

    @Autowired
    private final IHealthCareProfRepository healthCareProfRepository;

    public HealthCareProfValidatorAdapter(IHealthCareProfRepository healthCareProfRepository) {
        this.healthCareProfRepository = healthCareProfRepository;
    }

    @Override
    public boolean existsAndActive(String id) {
        return healthCareProfRepository.findById(new HealthCareProfId(id))
            .map(hcp -> Boolean.TRUE.equals(hcp.isActive()))
            .orElse(false);
    }
}

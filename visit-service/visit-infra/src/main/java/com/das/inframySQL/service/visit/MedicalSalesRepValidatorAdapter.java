package com.das.inframySQL.service.visit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;

@Service
public class MedicalSalesRepValidatorAdapter implements IMedicalSalesRepValidator {

    @Autowired
    private final IMedicalSalesRepRepository medicalSalesRepRepository;

    public MedicalSalesRepValidatorAdapter(IMedicalSalesRepRepository medicalSalesRepRepository) {
        this.medicalSalesRepRepository = medicalSalesRepRepository;
    }

    @Override
    public boolean existsAndActive(String id) {
        return medicalSalesRepRepository.findById(new MedicalSalesRepId(id))
            .map(msr -> Boolean.TRUE.equals(msr.isActive()))
            .orElse(false);
    }
}

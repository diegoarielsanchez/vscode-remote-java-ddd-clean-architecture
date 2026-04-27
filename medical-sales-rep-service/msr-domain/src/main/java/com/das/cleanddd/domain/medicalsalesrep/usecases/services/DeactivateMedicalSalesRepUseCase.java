package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepIDDto;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class DeactivateMedicalSalesRepUseCase implements UseCaseOnlyInput<MedicalSalesRepIDDto> {
   @Autowired
    private final IMedicalSalesRepRepository repository; 

    public DeactivateMedicalSalesRepUseCase(IMedicalSalesRepRepository repository) {
        this.repository = repository;
    }

    @Override
    public void execute(MedicalSalesRepIDDto inputDTO) throws DomainException {
        
        if(inputDTO.medicalSalesRepId()==null) {
            throw new DomainException("Medical Sales Representative Id is required.");
          }
        MedicalSalesRepId medicalSalesRepId = new MedicalSalesRepId(inputDTO.medicalSalesRepId());
        Optional<MedicalSalesRep> medicalSalesRep = repository.findById(medicalSalesRepId);
        if(!medicalSalesRep.isPresent()) {
            throw new DomainException("Medical Sales Representative not found.");
        }
        if(Boolean.TRUE.equals(medicalSalesRep.get().isActive())) {
            repository.save(medicalSalesRep.get().setDeactivate());
          }
    }
}


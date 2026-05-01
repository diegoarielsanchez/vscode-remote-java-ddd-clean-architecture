package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrDeactivatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepIDDto;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class DeactivateMedicalSalesRepUseCase implements UseCaseOnlyInput<MedicalSalesRepIDDto> {

    @Autowired
    private final IMedicalSalesRepRepository repository;
    private final IMsrEventPublisher publisher;

    public DeactivateMedicalSalesRepUseCase(IMedicalSalesRepRepository repository, IMsrEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
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
            MedicalSalesRep deactivated = medicalSalesRep.get().setDeactivate();
            repository.save(deactivated);
            publisher.publish(new MsrDeactivatedEvent(
                    deactivated.getId().value(),
                    deactivated.getName().value(),
                    deactivated.getSurname().value(),
                    deactivated.getEmail().value(),
                    deactivated.getActive().value()));
          }
    }
}


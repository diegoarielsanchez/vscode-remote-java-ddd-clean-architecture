package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepIDDto;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class ActivateMedicalSalesRepUseCase implements UseCaseOnlyInput<MedicalSalesRepIDDto> {
    
    @Autowired
    private final IMedicalSalesRepRepository repository;
    private final IMsrEventPublisher publisher;
    private final UnitOfWork unitOfWork;

    public ActivateMedicalSalesRepUseCase(IMedicalSalesRepRepository repository, IMsrEventPublisher publisher, UnitOfWork unitOfWork) {
        this.repository = repository;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
    }

    /**
     * Loading, changing, saving and recording the events happen in one unit of work, so the
     * aggregate and its outbox entries commit (or roll back) together.
     */
    @Override
    public void execute(MedicalSalesRepIDDto inputDTO) throws DomainException {
        unitOfWork.run(() -> doExecute(inputDTO));
    }

    private void doExecute(MedicalSalesRepIDDto inputDTO) throws DomainException {
        
        if(inputDTO.medicalSalesRepId()==null) {
            throw new DomainException("Medical Sales Representative Id is required.");
          }
        MedicalSalesRepId medicalSalesRepId = new MedicalSalesRepId(inputDTO.medicalSalesRepId());
        Optional<MedicalSalesRep> medicalSalesRep = repository.findById(medicalSalesRepId);
        if(!medicalSalesRep.isPresent()) {
            throw new DomainException("Medical Sales Representative not found.");
        }
        if(Boolean.FALSE.equals(medicalSalesRep.get().isActive())) {
            MedicalSalesRep activated = medicalSalesRep.get().setActivate();
            repository.save(activated);
            activated.pullDomainEvents().forEach(publisher::publish);
          }
    }
}

package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepEmail;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepName;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepMapper;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepOutputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.UpdateMedicalSalesRepInputDTO;
import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

@Service
public final class UpdateMedicalSalesRepUseCase implements UseCase<UpdateMedicalSalesRepInputDTO, MedicalSalesRepOutputDTO> {

    @Autowired
    private final IMedicalSalesRepRepository repository; 
    @Autowired
    private final MedicalSalesRepMapper mapper;
    private final IMsrEventPublisher publisher;
    private final EnsureMedicalSalesRepEmailIsUniqueService uniqueEmailService;

    public UpdateMedicalSalesRepUseCase(IMedicalSalesRepRepository repository
        , MedicalSalesRepMapper mapper
        , IMsrEventPublisher publisher
        ) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.uniqueEmailService = new EnsureMedicalSalesRepEmailIsUniqueService(repository);
    }
    @Override
    public MedicalSalesRepOutputDTO execute(UpdateMedicalSalesRepInputDTO inputDTO)
            throws DomainException {
        // Validate input
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        MedicalSalesRep medicalSalesRep;
        try {
            // Name/surname/email presence and format rules are enforced by their
            // respective Value Objects (MedicalSalesRepName, MedicalSalesRepEmail);
            // duplicating those checks here would just create a second, divergent
            // source of truth for the same invariant.
            MedicalSalesRepName medicalSalesRepName = new MedicalSalesRepName(inputDTO.name());
            MedicalSalesRepName medicalSalesRepSurname = new MedicalSalesRepName(inputDTO.surname());
            MedicalSalesRepEmail medicalSalesRepEmail = new MedicalSalesRepEmail(inputDTO.email());
            MedicalSalesRepId id = new MedicalSalesRepId(inputDTO.id());
        // fetch existing MedicalSalesRep from the repository
        Optional<MedicalSalesRep> existingMedicalSalesRep = repository.findById(id);
        if (!existingMedicalSalesRep.isPresent()) {
            throw new DomainException("Medical Sales Representative not found.");
        }
        // Validate Unique Email (cross-aggregate rule enforced via domain service; excludes this MSR's own id)
        uniqueEmailService.ensureUnique(medicalSalesRepEmail, id);
        medicalSalesRep = existingMedicalSalesRep.get().withUpdatedDetails(
                medicalSalesRepName,
                medicalSalesRepSurname,
                medicalSalesRepEmail);
        // Update the existing MedicalSalesRep with the new values
        repository.save(medicalSalesRep);
        medicalSalesRep.pullDomainEvents().forEach(publisher::publish);
        // Convert response to output and return
        return mapper.outputFromEntity(medicalSalesRep);
        } catch (IllegalArgumentException  e) {
            throw new DomainException(e.getMessage());
        }
    }
}

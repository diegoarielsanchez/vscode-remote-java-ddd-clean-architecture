package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepEmail;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepName;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.CreateMedicalSalesRepInputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepMapper;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepOutputDTO;
import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

//@RequiredArgsConstructor
@Service
public final class CreateMedicalSalesRepUseCase implements UseCase<CreateMedicalSalesRepInputDTO, MedicalSalesRepOutputDTO> {

    @Autowired
    private final IMedicalSalesRepRepository repository; 
    @Autowired
    private final MedicalSalesRepMapper mapper;
    private final IMsrEventPublisher publisher;
    private final EnsureMedicalSalesRepEmailIsUniqueService uniqueEmailService;
    
    public CreateMedicalSalesRepUseCase(IMedicalSalesRepRepository repository
        , MedicalSalesRepMapper mapper
        , IMsrEventPublisher publisher
        ) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.uniqueEmailService = new EnsureMedicalSalesRepEmailIsUniqueService(repository);
    }

    @Override
    public MedicalSalesRepOutputDTO execute(CreateMedicalSalesRepInputDTO inputDTO)
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
            // Validate Unique Email (cross-aggregate rule enforced via domain service)
            uniqueEmailService.ensureUnique(medicalSalesRepEmail, null);
            // Create a new MedicalSalesRep object using the factory
                medicalSalesRep = MedicalSalesRep.create(null, medicalSalesRepName, medicalSalesRepSurname, medicalSalesRepEmail, null);
            // Create
            repository.save(medicalSalesRep);
                medicalSalesRep.pullDomainEvents().forEach(publisher::publish);
            // Convert response to output and return
            return mapper.outputFromEntity(medicalSalesRep);
        } catch (IllegalArgumentException  e) {
            throw new DomainException(e.getMessage());

        }
    }

}

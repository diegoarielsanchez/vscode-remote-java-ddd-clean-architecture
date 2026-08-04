package com.das.cleanddd.domain.healthcareprof.usecases.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfEmail;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfName;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.entities.Specialty;
import com.das.cleanddd.domain.healthcareprof.entities.SpecialtyCatalog;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.CreateHealthCareProfInputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfMapper;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfOutputDTO;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

//@RequiredArgsConstructor
@Service
public final class CreateHealthCareProfUseCase implements UseCase<CreateHealthCareProfInputDTO, HealthCareProfOutputDTO> {

    @Autowired
    private final IHealthCareProfRepository repository; 
    @Autowired
    private final HealthCareProfMapper mapper;
    private final IHcpEventPublisher publisher;
    private final EnsureHealthCareProfEmailIsUniqueService uniqueEmailService;
    
    public CreateHealthCareProfUseCase(IHealthCareProfRepository repository
        , HealthCareProfMapper mapper
        , IHcpEventPublisher publisher
        ) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
        this.uniqueEmailService = new EnsureHealthCareProfEmailIsUniqueService(repository);
    }

    @Override
    public HealthCareProfOutputDTO execute(CreateHealthCareProfInputDTO inputDTO)
            throws DomainException {

        // Validate input
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        HealthCareProf entity;

        try {
            // Name/surname/email presence and format rules are enforced by their
            // respective Value Objects (HealthCareProfName, HealthCareProfEmail);
            // duplicating those checks here would just create a second, divergent
            // source of truth for the same invariant.
            HealthCareProfName name = new HealthCareProfName(inputDTO.name());
            HealthCareProfName surname = new HealthCareProfName(inputDTO.surname());
            HealthCareProfEmail email = new HealthCareProfEmail(inputDTO.email());
            List<Specialty> specialties = (inputDTO.specialties() == null ? List.<String>of() : inputDTO.specialties()).stream()
                .map(code -> {
                    try {
                        return SpecialtyCatalog.fromCode(code);
                    } catch (DomainException e) {
                        throw new IllegalArgumentException(e.getMessage());
                    }
                })
                .toList();
            // Validate Unique Email (cross-aggregate rule enforced via domain service)
            uniqueEmailService.ensureUnique(email, null);
            // Create a new HealthCareProf object using the factory
                entity = HealthCareProf.create(null, name, surname, email, null, specialties);
            // "Specialties required" (and other entity-level invariants) is enforced
            // by the entity's own validate() method - the single source of truth,
            // reused by both create and update instead of duplicated DTO checks.
            entity.validate();
            // Create
            repository.save(entity);
                entity.pullDomainEvents().forEach(publisher::publish);
            // Convert response to output and return
            return mapper.outputFromEntity(entity);
        } catch (IllegalArgumentException | BusinessException e) {
            throw new DomainException(e.getMessage());

        }
    }

}

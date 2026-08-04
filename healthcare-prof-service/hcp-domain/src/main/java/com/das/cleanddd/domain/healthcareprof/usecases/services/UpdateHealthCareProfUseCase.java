package com.das.cleanddd.domain.healthcareprof.usecases.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfEmail;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfName;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.entities.Specialty;
import com.das.cleanddd.domain.healthcareprof.entities.SpecialtyCatalog;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfMapper;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfOutputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.UpdateHealthCareProfInputDTO;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

@Service
public final class UpdateHealthCareProfUseCase implements UseCase<UpdateHealthCareProfInputDTO, HealthCareProfOutputDTO> {

    @Autowired
    private final IHealthCareProfRepository _repository; 
    @Autowired
    private final HealthCareProfMapper _mapper;
    private final IHcpEventPublisher _publisher;
    private final EnsureHealthCareProfEmailIsUniqueService _uniqueEmailService;

    public UpdateHealthCareProfUseCase(IHealthCareProfRepository repository
        , HealthCareProfMapper mapper
        , IHcpEventPublisher publisher
        ) {
        this._repository = repository;
        this._mapper = mapper;
        this._publisher = publisher;
        this._uniqueEmailService = new EnsureHealthCareProfEmailIsUniqueService(repository);
    }
    @Override
    public HealthCareProfOutputDTO execute(UpdateHealthCareProfInputDTO inputDTO)
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
            HealthCareProfId id = new HealthCareProfId(inputDTO.id());
            List<Specialty> specialties = (inputDTO.specialties() == null ? List.<String>of() : inputDTO.specialties()).stream()
                .map(code -> {
                    try {
                        return SpecialtyCatalog.fromCode(code);
                    } catch (DomainException e) {
                        throw new IllegalArgumentException(e.getMessage());
                    }
                })
                .toList();
        // fetch existing HealthCareProf from the repository
        Optional<HealthCareProf> existingHealthCareProf = _repository.findById(id);
        if (!existingHealthCareProf.isPresent()) {
            throw new DomainException("Health Care Professional not found.");
        }
        // Validate Unique Email (cross-aggregate rule enforced via domain service; excludes this HCP's own id)
        _uniqueEmailService.ensureUnique(email, id);
        entity = existingHealthCareProf.get().withUpdatedDetails(name, surname, email, specialties);
        // "Specialties required" (and other entity-level invariants) is enforced
        // by the entity's own validate() method - the single source of truth,
        // reused by both create and update instead of duplicated DTO checks.
        entity.validate();
        // Update the existing HealthCareProf with the new values
        _repository.save(entity);
        entity.pullDomainEvents().forEach(_publisher::publish);
        // Convert response to output and return
        return _mapper.outputFromEntity(entity);
        } catch (IllegalArgumentException | BusinessException e) {
            throw new DomainException(e.getMessage());
        }
    }
}

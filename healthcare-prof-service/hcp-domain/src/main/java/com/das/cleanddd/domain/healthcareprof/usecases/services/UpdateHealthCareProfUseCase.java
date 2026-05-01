package com.das.cleanddd.domain.healthcareprof.usecases.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfEmail;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfFactory;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfId;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfName;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.entities.Specialty;
import com.das.cleanddd.domain.healthcareprof.entities.SpecialtyCatalog;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfMapper;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfOutputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.UpdateHealthCareProfInputDTO;
import com.das.cleanddd.domain.healthcareprof.events.HcpUpdatedEvent;
import com.das.cleanddd.domain.healthcareprof.ports.IHcpEventPublisher;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

@Service
public final class UpdateHealthCareProfUseCase implements UseCase<UpdateHealthCareProfInputDTO, HealthCareProfOutputDTO> {

    @Autowired
    private final IHealthCareProfRepository _repository; 
    @Autowired
    private final HealthCareProfFactory _factory;
    @Autowired
    private final HealthCareProfMapper _mapper;
    private final IHcpEventPublisher _publisher;

    public UpdateHealthCareProfUseCase(IHealthCareProfRepository repository
        , HealthCareProfFactory factory
        , HealthCareProfMapper mapper
        , IHcpEventPublisher publisher
        ) {
        this._repository = repository;
        this._factory = factory;
        this._mapper = mapper;
        this._publisher = publisher;
    }
    @Override
    public HealthCareProfOutputDTO execute(UpdateHealthCareProfInputDTO inputDTO)
            throws DomainException {
        // Validate input
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        if (inputDTO.name() == null || inputDTO.name().isEmpty()) {
            throw new DomainException("Name cannot be null or empty");
        }
        if (inputDTO.surname() == null || inputDTO.surname().isEmpty()) {
            throw new DomainException("Surname cannot be null or empty");
        }
        if (inputDTO.email() == null || inputDTO.email().isEmpty()) {
            throw new DomainException("Email cannot be null or empty");
        }
        if (inputDTO.specialties() == null || inputDTO.specialties().isEmpty()) {
            throw new DomainException("Specialties cannot be null or empty");
        }
        HealthCareProf entity;
        HealthCareProf entityActivateStatus;
        try {
            HealthCareProfName name = new HealthCareProfName(inputDTO.name());
            HealthCareProfName surname = new HealthCareProfName(inputDTO.surname());
            HealthCareProfEmail email = new HealthCareProfEmail(inputDTO.email());
            HealthCareProfId id = new HealthCareProfId(inputDTO.id());
            List<Specialty> specialties = inputDTO.specialties().stream()
                .map(code -> {
                    try {
                        return SpecialtyCatalog.fromCode(code);
                    } catch (DomainException e) {
                        throw new IllegalArgumentException(e.getMessage());
                    }
                })
                .toList();
            // Create a new HealthCareProf object using the factory
            entity = _factory.recreateExistingHealthCareProf(id, name, surname, email, null, specialties);
        // fetch existing HealthCareProf from the repository
        Optional<HealthCareProf> existingHealthCareProf = _repository.findById(id);
        if (!existingHealthCareProf.isPresent()) {
            throw new DomainException("Health Care Professional not found.");
        }
        // Validate Unique Email
        if (!existingHealthCareProf.get().getEmail().equals(email)) {
            Optional<HealthCareProf> HealthCareProfRepWithEmail = _repository.findByEmail(email);
            if (HealthCareProfRepWithEmail.isPresent()) {
                throw new DomainException("There is already a Health Care Professional with this email.");
            }
        }
        // keep the existing HealthCareProf active status
        if (Boolean.TRUE.equals(existingHealthCareProf.get().isActive())) {
            entityActivateStatus = entity.setActivate();
        } else {
            entityActivateStatus = entity.setDeactivate();
        }
        // Update the existing HealthCareProf with the new values
        _repository.save(entityActivateStatus);
        // Publish domain event
        _publisher.publish(new HcpUpdatedEvent(
                entityActivateStatus.getId().toString(),
                entityActivateStatus.getFirstName(),
                entityActivateStatus.getLastName(),
                entityActivateStatus.getEmail().toString(),
                entityActivateStatus.isActive()));
        // Convert response to output and return
        return _mapper.outputFromEntity(entityActivateStatus);
        } catch (BusinessException | IllegalArgumentException  e) {
            throw new DomainException(e.getMessage());
        }
    }
}

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
import com.das.cleanddd.domain.shared.exceptions.DomainException;

@Service
public final class UpdateHealthCareProfUseCase implements UseCase<UpdateHealthCareProfInputDTO, HealthCareProfOutputDTO> {

    @Autowired
    private final IHealthCareProfRepository _repository; 
    @Autowired
    private final HealthCareProfMapper _mapper;
    private final IHcpEventPublisher _publisher;

    public UpdateHealthCareProfUseCase(IHealthCareProfRepository repository
        , HealthCareProfMapper mapper
        , IHcpEventPublisher publisher
        ) {
        this._repository = repository;
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
        entity = existingHealthCareProf.get().withUpdatedDetails(name, surname, email, specialties);
        // Update the existing HealthCareProf with the new values
        _repository.save(entity);
        entity.pullDomainEvents().forEach(_publisher::publish);
        // Convert response to output and return
        return _mapper.outputFromEntity(entity);
        } catch (IllegalArgumentException  e) {
            throw new DomainException(e.getMessage());
        }
    }
}

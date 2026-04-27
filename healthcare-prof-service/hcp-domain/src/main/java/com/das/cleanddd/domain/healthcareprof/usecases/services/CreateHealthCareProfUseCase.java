package com.das.cleanddd.domain.healthcareprof.usecases.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfEmail;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfFactory;
import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProfName;
import com.das.cleanddd.domain.healthcareprof.entities.IHealthCareProfRepository;
import com.das.cleanddd.domain.healthcareprof.entities.Specialty;
import com.das.cleanddd.domain.healthcareprof.entities.SpecialtyCatalog;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.CreateHealthCareProfInputDTO;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfMapper;
import com.das.cleanddd.domain.healthcareprof.usecases.dtos.HealthCareProfOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

//@RequiredArgsConstructor
@Service
public final class CreateHealthCareProfUseCase implements UseCase<CreateHealthCareProfInputDTO, HealthCareProfOutputDTO> {

    @Autowired
    private final IHealthCareProfRepository repository; 
    @Autowired
    private final HealthCareProfFactory factory;
    @Autowired
    private final HealthCareProfMapper mapper;
    
    public CreateHealthCareProfUseCase(IHealthCareProfRepository repository
        , HealthCareProfFactory factory
        , HealthCareProfMapper mapper
        ) {
        this.repository = repository;
        this.factory = factory;
        this.mapper = mapper;   
    }

    @Override
    public HealthCareProfOutputDTO execute(CreateHealthCareProfInputDTO inputDTO)
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
            List<Specialty> specialties = inputDTO.specialties().stream()
                .map(code -> {
                    try {
                        return SpecialtyCatalog.fromCode(code);
                    } catch (DomainException e) {
                        throw new IllegalArgumentException(e.getMessage());
                    }
                })
                .toList();
            // Validate Unique Email
            Optional<HealthCareProf> entityWithEmail = repository.findByEmail(email);
            if(entityWithEmail.isPresent()) {
            throw new DomainException("There is already a Health Care Professional with this email.");
            }
            // Create a new HealthCareProf object using the factory
            entity = factory.createHealthCareProf(name, surname, email, specialties);
            // Create
            repository.save(entity);
            // Convert response to output and return
            return mapper.outputFromEntity(entity);
        } catch (BusinessException | IllegalArgumentException  e) {
            throw new DomainException(e.getMessage());

        }
    }

}

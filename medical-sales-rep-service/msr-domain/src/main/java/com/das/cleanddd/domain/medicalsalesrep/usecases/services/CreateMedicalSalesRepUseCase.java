package com.das.cleanddd.domain.medicalsalesrep.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepEmail;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepFactory;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRepName;
import com.das.cleanddd.domain.medicalsalesrep.entities.IMedicalSalesRepRepository;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.CreateMedicalSalesRepInputDTO;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepMapper;
import com.das.cleanddd.domain.medicalsalesrep.usecases.dtos.MedicalSalesRepOutputDTO;
import com.das.cleanddd.domain.medicalsalesrep.events.MsrCreatedEvent;
import com.das.cleanddd.domain.medicalsalesrep.ports.IMsrEventPublisher;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.BusinessException;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

//@RequiredArgsConstructor
@Service
public final class CreateMedicalSalesRepUseCase implements UseCase<CreateMedicalSalesRepInputDTO, MedicalSalesRepOutputDTO> {

    @Autowired
    private final IMedicalSalesRepRepository repository; 
    @Autowired
    private final MedicalSalesRepFactory factory;
    @Autowired
    private final MedicalSalesRepMapper mapper;
    private final IMsrEventPublisher publisher;
    
    public CreateMedicalSalesRepUseCase(IMedicalSalesRepRepository repository
        , MedicalSalesRepFactory factory
        , MedicalSalesRepMapper mapper
        , IMsrEventPublisher publisher
        ) {
        this.repository = repository;
        this.factory = factory;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    @Override
    public MedicalSalesRepOutputDTO execute(CreateMedicalSalesRepInputDTO inputDTO)
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
        MedicalSalesRep medicalSalesRep;

        try {
            MedicalSalesRepName medicalSalesRepName = new MedicalSalesRepName(inputDTO.name());
            MedicalSalesRepName medicalSalesRepSurname = new MedicalSalesRepName(inputDTO.surname());
            MedicalSalesRepEmail medicalSalesRepEmail = new MedicalSalesRepEmail(inputDTO.email());
            // Validate Unique Email
            Optional<MedicalSalesRep> medicalSalesRepWithEmail = repository.findByEmail(medicalSalesRepEmail);
            if(medicalSalesRepWithEmail.isPresent()) {
            throw new DomainException("There is already a Medical Sales Representative with this email.");
            }
            // Create a new MedicalSalesRep object using the factory
            medicalSalesRep = factory.createMedicalSalesRep(medicalSalesRepName, medicalSalesRepSurname, medicalSalesRepEmail);
            // Create
            repository.save(medicalSalesRep);
            publisher.publish(new MsrCreatedEvent(
                    medicalSalesRep.getId().value(),
                    medicalSalesRep.getName().value(),
                    medicalSalesRep.getSurname().value(),
                    medicalSalesRep.getEmail().value(),
                    medicalSalesRep.getActive().value()));
            // Convert response to output and return
            return mapper.outputFromEntity(medicalSalesRep);
        } catch (BusinessException | IllegalArgumentException  e) {
            throw new DomainException(e.getMessage());

        }
    }

}

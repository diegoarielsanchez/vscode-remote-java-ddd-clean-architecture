package com.das.cleanddd.domain.visit.usecases.services;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.Visit;
import com.das.cleanddd.domain.visit.entities.VisitFactory;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitOutputDTO;

@Service
public final class CreateVisitUseCase implements UseCase<CreateVisitInputDTO, VisitOutputDTO> {

    @Autowired
    private final IVisitRepository visitRepository;
    @Autowired
    private final IHealthCareProfValidator healthCareProfValidator;
    @Autowired
    private final IMedicalSalesRepValidator medicalSalesRepValidator;
    @Autowired
    private final VisitFactory factory;
    @Autowired
    private final VisitMapper mapper;

    public CreateVisitUseCase(
        IVisitRepository visitRepository,
        IHealthCareProfValidator healthCareProfValidator,
        IMedicalSalesRepValidator medicalSalesRepValidator,
        VisitFactory factory,
        VisitMapper mapper
    ) {
        this.visitRepository = visitRepository;
        this.healthCareProfValidator = healthCareProfValidator;
        this.medicalSalesRepValidator = medicalSalesRepValidator;
        this.factory = factory;
        this.mapper = mapper;
    }

    @Override
    public VisitOutputDTO execute(CreateVisitInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        if (inputDTO.visitDate() == null) {
            throw new DomainException("Visit date cannot be null");
        }
        if (inputDTO.healthCareProfId() == null || inputDTO.healthCareProfId().isBlank()) {
            throw new DomainException("Health Care Professional id cannot be null or empty");
        }
        if (inputDTO.visitSiteId() == null || inputDTO.visitSiteId().isBlank()) {
            throw new DomainException("Visit site id cannot be null or empty");
        }
        if (inputDTO.medicalSalesRepId() == null || inputDTO.medicalSalesRepId().isBlank()) {
            throw new DomainException("Medical Sales Representative id cannot be null or empty");
        }

        try {
            HealthCareProfId healthCareProfId = new HealthCareProfId(inputDTO.healthCareProfId());
            MedicalSalesRepId medicalSalesRepId = new MedicalSalesRepId(inputDTO.medicalSalesRepId());
            Identifier visitSiteId = new Identifier(inputDTO.visitSiteId()) {};

            if (!healthCareProfValidator.existsAndActive(inputDTO.healthCareProfId())) {
                throw new DomainException("Health Care Professional not found or not active");
            }

            if (!medicalSalesRepValidator.existsAndActive(inputDTO.medicalSalesRepId())) {
                throw new DomainException("Medical Sales Representative not found or not active");
            }

            LocalDateTime visitDateTime = inputDTO.visitDate().atStartOfDay();

            if (visitRepository.existsByVisitKey(healthCareProfId, medicalSalesRepId, visitDateTime)) {
                throw new DomainException("A visit already exists for this Health Care Professional, Medical Sales Representative and date.");
            }

            TextValueObject comments = inputDTO.visitComments() == null
                ? null
                : new TextValueObject(inputDTO.visitComments()) {};

            Visit visit = factory.createVisit(
                visitDateTime,
                healthCareProfId,
                comments,
                visitSiteId,
                medicalSalesRepId
            );

            visitRepository.save(visit);
            return mapper.outputFromEntity(visit);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}

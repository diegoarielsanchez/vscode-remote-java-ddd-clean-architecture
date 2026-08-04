package com.das.cleanddd.domain.visit.usecases.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.VisitDateTime;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.entities.VisitPlanFactory;
import com.das.cleanddd.domain.visit.usecases.dtos.CreateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;

@Service
public final class CreateVisitPlanUseCase implements UseCase<CreateVisitPlanInputDTO, VisitPlanOutputDTO> {

    @Autowired
    private final IVisitPlanRepository visitPlanRepository;
    @Autowired
    private final VisitPlanFactory factory;
    @Autowired
    private final VisitPlanMapper mapper;

    public CreateVisitPlanUseCase(
        IVisitPlanRepository visitPlanRepository,
        VisitPlanFactory factory,
        VisitPlanMapper mapper
    ) {
        this.visitPlanRepository = visitPlanRepository;
        this.factory = factory;
        this.mapper = mapper;
    }

    @Override
    public VisitPlanOutputDTO execute(CreateVisitPlanInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        if (inputDTO.visitDateTime() == null) {
            throw new DomainException("Visit date time cannot be null");
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

            if (visitPlanRepository.existsDuplicateOnDay(
                    inputDTO.visitDateTime().toLocalDate(),
                    inputDTO.healthCareProfId(),
                    inputDTO.medicalSalesRepId(),
                    null)) {
                throw new DomainException(
                    "A visit plan for this Healthcare Professional and Medical Sales Representative already exists on the same day.");
            }

            TextValueObject comments = inputDTO.visitComments() == null
                ? null
                : new TextValueObject(inputDTO.visitComments()) {};

            VisitPlan visitPlan = factory.createVisitPlan(
                new VisitDateTime(inputDTO.visitDateTime()),
                healthCareProfId,
                comments,
                visitSiteId,
                medicalSalesRepId
            );

            visitPlanRepository.save(visitPlan);
            return mapper.outputFromEntity(visitPlan);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}

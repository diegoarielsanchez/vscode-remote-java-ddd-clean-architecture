package com.das.cleanddd.domain.visit.usecases.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;

@Service
public final class UpdateVisitPlanUseCase implements UseCase<UpdateVisitPlanInputDTO, VisitPlanOutputDTO> {

    @Autowired
    private final IVisitPlanRepository visitPlanRepository;
    @Autowired
    private final IHealthCareProfValidator healthCareProfValidator;
    @Autowired
    private final IMedicalSalesRepValidator medicalSalesRepValidator;
    @Autowired
    private final VisitPlanMapper mapper;

    public UpdateVisitPlanUseCase(
        IVisitPlanRepository visitPlanRepository,
        IHealthCareProfValidator healthCareProfValidator,
        IMedicalSalesRepValidator medicalSalesRepValidator,
        VisitPlanMapper mapper
    ) {
        this.visitPlanRepository = visitPlanRepository;
        this.healthCareProfValidator = healthCareProfValidator;
        this.medicalSalesRepValidator = medicalSalesRepValidator;
        this.mapper = mapper;
    }

    @Override
    public VisitPlanOutputDTO execute(UpdateVisitPlanInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        if (inputDTO.id() == null || inputDTO.id().isBlank()) {
            throw new DomainException("Visit plan id cannot be null or empty");
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
            VisitId visitId = new VisitId(inputDTO.id());
            Optional<VisitPlan> existingVisitPlan = visitPlanRepository.search(visitId);
            if (existingVisitPlan.isEmpty()) {
                throw new DomainException("Visit plan not found.");
            }

            HealthCareProfId healthCareProfId = new HealthCareProfId(inputDTO.healthCareProfId());
            if (!healthCareProfValidator.existsAndActive(inputDTO.healthCareProfId())) {
                throw new DomainException("Health Care Professional not found or not active");
            }

            MedicalSalesRepId medicalSalesRepId = new MedicalSalesRepId(inputDTO.medicalSalesRepId());
            if (!medicalSalesRepValidator.existsAndActive(inputDTO.medicalSalesRepId())) {
                throw new DomainException("Medical Sales Representative not found or not active");
            }

            Identifier visitSiteId = new Identifier(inputDTO.visitSiteId()) {};
            TextValueObject comments = inputDTO.visitComments() == null
                ? null
                : new TextValueObject(inputDTO.visitComments()) {};

            VisitPlan updatedVisitPlan = new VisitPlan(
                visitId,
                inputDTO.visitDateTime(),
                healthCareProfId,
                comments,
                visitSiteId,
                List.of(),
                medicalSalesRepId
            );

            visitPlanRepository.save(updatedVisitPlan);
            return mapper.outputFromEntity(updatedVisitPlan);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}

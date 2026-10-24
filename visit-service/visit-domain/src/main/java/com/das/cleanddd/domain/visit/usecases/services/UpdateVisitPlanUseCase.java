package com.das.cleanddd.domain.visit.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.audit.ChangeReason;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.visit.IVisitPlanRepository;
import com.das.cleanddd.domain.visit.entities.HealthCareProfId;
import com.das.cleanddd.domain.visit.entities.MedicalSalesRepId;
import com.das.cleanddd.domain.visit.entities.VisitDateTime;
import com.das.cleanddd.domain.visit.entities.VisitId;
import com.das.cleanddd.domain.visit.entities.VisitPlan;
import com.das.cleanddd.domain.visit.entities.VisitPlanFactory;
import com.das.cleanddd.domain.visit.usecases.dtos.UpdateVisitPlanInputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanMapper;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitPlanOutputDTO;
import com.das.cleanddd.domain.visit.usecases.dtos.VisitItemDTO;

@Service
public final class UpdateVisitPlanUseCase implements UseCase<UpdateVisitPlanInputDTO, VisitPlanOutputDTO> {

    @Autowired
    private final IVisitPlanRepository visitPlanRepository;
    @Autowired
    private final VisitPlanFactory factory;
    @Autowired
    private final VisitPlanMapper mapper;

    public UpdateVisitPlanUseCase(
        IVisitPlanRepository visitPlanRepository,
        VisitPlanFactory factory,
        VisitPlanMapper mapper
    ) {
        this.visitPlanRepository = visitPlanRepository;
        this.factory = factory;
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
            ChangeReason reason = new ChangeReason(inputDTO.reason());
            Optional<VisitPlan> existingVisitPlan = visitPlanRepository.search(visitId);
            // Another MSR's plan answers exactly like a missing one, so plan ids cannot be probed
            // and a plan cannot be reassigned to a different MSR (OWASP A01).
            if (existingVisitPlan.isEmpty()
                    || !existingVisitPlan.get().medicalSalesRepId().value().equals(inputDTO.medicalSalesRepId())) {
                throw new DomainException("Visit plan not found.");
            }

            HealthCareProfId healthCareProfId = new HealthCareProfId(inputDTO.healthCareProfId());
            MedicalSalesRepId medicalSalesRepId = new MedicalSalesRepId(inputDTO.medicalSalesRepId());

            if (visitPlanRepository.existsDuplicateOnDay(
                    inputDTO.visitDateTime().toLocalDate(),
                    inputDTO.healthCareProfId(),
                    inputDTO.medicalSalesRepId(),
                    inputDTO.id())) {
                throw new DomainException(
                    "A visit plan for this Healthcare Professional and Medical Sales Representative already exists on the same day.");
            }

            Identifier visitSiteId = new Identifier(inputDTO.visitSiteId()) {};
            TextValueObject comments = inputDTO.visitComments() == null
                ? null
                : new TextValueObject(inputDTO.visitComments()) {};

            VisitPlan updatedVisitPlan = factory.buildForUpdate(
                visitId,
                new VisitDateTime(inputDTO.visitDateTime()),
                healthCareProfId,
                comments,
                visitSiteId,
                medicalSalesRepId,
                null,
                // Omitted items keep the plan's current ones; an empty list clears them.
                inputDTO.items() == null
                    ? existingVisitPlan.get().visitItems()
                    : VisitItemDTO.toEntities(inputDTO.items())
            );

            updatedVisitPlan.recordUpdatedFrom(existingVisitPlan.get().auditState(), reason);
            visitPlanRepository.save(updatedVisitPlan);
            return mapper.outputFromEntity(updatedVisitPlan);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}

package com.das.cleanddd.domain.visit.entities;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import com.das.cleanddd.domain.visit.ports.IHealthCareProfValidator;
import com.das.cleanddd.domain.visit.ports.IMedicalSalesRepValidator;

@Service
public class VisitPlanFactory {

    private final IHealthCareProfValidator healthCareProfValidator;
    private final IMedicalSalesRepValidator medicalSalesRepValidator;

    public VisitPlanFactory(
        IHealthCareProfValidator healthCareProfValidator,
        IMedicalSalesRepValidator medicalSalesRepValidator
    ) {
        this.healthCareProfValidator = healthCareProfValidator;
        this.medicalSalesRepValidator = medicalSalesRepValidator;
    }

    public VisitPlan createVisitPlan(
        LocalDateTime visitDateTime,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRepId medicalSalesRepId
    ) throws BusinessValidationException {
        validateActiveParticipants(healthCareProfId, medicalSalesRepId);
        return new VisitPlan(
            new VisitId(UUID.randomUUID().toString()),
            visitDateTime,
            healthCareProfId,
            visitComments,
            visitSiteId,
            List.of(),
            medicalSalesRepId
        );
    }

    public VisitPlan buildForUpdate(
        VisitId visitId,
        LocalDateTime visitDateTime,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRepId medicalSalesRepId
    ) throws BusinessValidationException {
        validateActiveParticipants(healthCareProfId, medicalSalesRepId);
        return new VisitPlan(
            visitId,
            visitDateTime,
            healthCareProfId,
            visitComments,
            visitSiteId,
            List.of(),
            medicalSalesRepId
        );
    }

    private void validateActiveParticipants(
        HealthCareProfId healthCareProfId,
        MedicalSalesRepId medicalSalesRepId
    ) throws BusinessValidationException {
        if (!healthCareProfValidator.existsAndActive(healthCareProfId.value())) {
            throw new BusinessValidationException("Health Care Professional not found or not active.");
        }
        if (!medicalSalesRepValidator.existsAndActive(medicalSalesRepId.value())) {
            throw new BusinessValidationException("Medical Sales Representative not found or not active.");
        }
    }
}

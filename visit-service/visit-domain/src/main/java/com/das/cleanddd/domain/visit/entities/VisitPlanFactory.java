package com.das.cleanddd.domain.visit.entities;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.AddressValueObject;
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
        VisitDateTime visitDateTime,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRepId medicalSalesRepId
    ) throws BusinessValidationException {
        return createVisitPlan(visitDateTime, healthCareProfId, visitComments, visitSiteId, medicalSalesRepId, null);
    }

    public VisitPlan createVisitPlan(
        VisitDateTime visitDateTime,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRepId medicalSalesRepId,
        AddressValueObject address
    ) throws BusinessValidationException {
        if (visitDateTime == null || visitDateTime.value().toLocalDate().isBefore(LocalDate.now())) {
            throw new BusinessValidationException("Visit date/time cannot be in the past.");
        }
        validateActiveParticipants(healthCareProfId, medicalSalesRepId);
        return new VisitPlan(
            new VisitId(UUID.randomUUID().toString()),
            visitDateTime,
            healthCareProfId,
            visitComments,
            visitSiteId,
            List.of(),
            medicalSalesRepId,
            true,
            address
        );
    }

    public VisitPlan buildForUpdate(
        VisitId visitId,
        VisitDateTime visitDateTime,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRepId medicalSalesRepId
    ) throws BusinessValidationException {
        return buildForUpdate(visitId, visitDateTime, healthCareProfId, visitComments, visitSiteId, medicalSalesRepId, null);
    }

    public VisitPlan buildForUpdate(
        VisitId visitId,
        VisitDateTime visitDateTime,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRepId medicalSalesRepId,
        AddressValueObject address
    ) throws BusinessValidationException {
        if (visitDateTime == null || visitDateTime.value().toLocalDate().isBefore(LocalDate.now())) {
            throw new BusinessValidationException("Visit date/time cannot be in the past.");
        }
        validateActiveParticipants(healthCareProfId, medicalSalesRepId);
        return new VisitPlan(
            visitId,
            visitDateTime,
            healthCareProfId,
            visitComments,
            visitSiteId,
            List.of(),
            medicalSalesRepId,
            true,
            address
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

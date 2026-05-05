package com.das.cleanddd.domain.visit.entities;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@Service
public class VisitPlanFactory {

    public VisitPlan createVisitPlan(
        LocalDateTime visitDateTime,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRepId medicalSalesRepId
    ) throws BusinessValidationException {
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
}

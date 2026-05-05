package com.das.cleanddd.domain.visit.entities;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@Service
public class VisitFactory {

    public Visit createVisit(
        LocalDateTime visitDate,
        HealthCareProfId healthCareProfId,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRepId medicalSalesRepId
    ) throws BusinessValidationException {
        return new Visit(
            new VisitId(UUID.randomUUID().toString()),
            visitDate,
            healthCareProfId,
            visitComments,
            visitSiteId,
            List.of(),
            medicalSalesRepId
        );
    }
}

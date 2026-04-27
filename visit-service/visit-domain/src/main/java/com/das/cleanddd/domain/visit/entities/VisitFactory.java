package com.das.cleanddd.domain.visit.entities;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.healthcareprof.entities.HealthCareProf;
import com.das.cleanddd.domain.medicalsalesrep.entities.MedicalSalesRep;
import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.shared.TextValueObject;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

@Service
public class VisitFactory {

    public Visit createVisit(
        LocalDateTime visitDate,
        HealthCareProf healthCareProf,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRep medicalSalesRep
    ) throws BusinessValidationException {
        return new Visit(
            new VisitId(UUID.randomUUID().toString()),
            visitDate,
            healthCareProf,
            visitComments,
            visitSiteId,
            List.of(),
            medicalSalesRep
        );
    }
}

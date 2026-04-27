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
public class VisitPlanFactory {

    public VisitPlan createVisitPlan(
        LocalDateTime visitDateTime,
        HealthCareProf healthCareProf,
        TextValueObject visitComments,
        Identifier visitSiteId,
        MedicalSalesRep medicalSalesRep
    ) throws BusinessValidationException {
        return new VisitPlan(
            new VisitId(UUID.randomUUID().toString()),
            visitDateTime,
            healthCareProf,
            visitComments,
            visitSiteId,
            List.of(),
            medicalSalesRep
        );
    }
}

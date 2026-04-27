package com.das.cleanddd.domain.visit.usecases.dtos;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.visit.entities.VisitPlan;

@Service
public class VisitPlanMapper {

    public VisitPlanOutputDTO outputFromEntity(VisitPlan visitPlan) {
        return new VisitPlanOutputDTO(
            visitPlan.visitId().value(),
            visitPlan.visitTimeDate(),
            visitPlan.healthCareProf() == null ? null : visitPlan.healthCareProf().id().value(),
            visitPlan.visitComments() == null ? null : visitPlan.visitComments().value(),
            visitPlan.visitSideId() == null ? null : visitPlan.visitSideId().value(),
            visitPlan.medicalSalesRep() == null ? null : visitPlan.medicalSalesRep().getId().value()
        );
    }

    public List<VisitPlanOutputDTO> outputFromEntityList(List<VisitPlan> visitPlans) {
        return visitPlans.stream().map(this::outputFromEntity).toList();
    }
}

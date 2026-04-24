package com.das.cleanddd.domain.visit.usecases.dtos;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.visit.entities.Visit;

@Service
public class VisitMapper {

    public VisitOutputDTO outputFromEntity(Visit visit) {
        return new VisitOutputDTO(
            visit.visitId().value(),
            visit.visitDate(),
            visit.healthCareProf() == null ? null : visit.healthCareProf().id().value(),
            visit.visitComments() == null ? null : visit.visitComments().value(),
            visit.visitSideId() == null ? null : visit.visitSideId().value(),
            visit.medicalSalesRep() == null ? null : visit.medicalSalesRep().getId().value()
        );
    }

    public List<VisitOutputDTO> outputFromEntityList(List<Visit> visits) {
        return visits.stream().map(this::outputFromEntity).toList();
    }
}

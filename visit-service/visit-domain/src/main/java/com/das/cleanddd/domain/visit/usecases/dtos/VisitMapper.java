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
            visit.healthCareProfId() == null ? null : visit.healthCareProfId().value(),
            visit.visitComments() == null ? null : visit.visitComments().value(),
            visit.visitSideId() == null ? null : visit.visitSideId().value(),
            visit.medicalSalesRepId() == null ? null : visit.medicalSalesRepId().value()
        );
    }

    public List<VisitOutputDTO> outputFromEntityList(List<Visit> visits) {
        return visits.stream().map(this::outputFromEntity).toList();
    }
}

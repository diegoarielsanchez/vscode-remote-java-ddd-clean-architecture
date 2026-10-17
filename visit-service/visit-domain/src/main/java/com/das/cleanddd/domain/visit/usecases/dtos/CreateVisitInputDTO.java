package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.LocalDate;
import java.util.List;

import com.das.cleanddd.domain.visit.entities.VisitItem;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVisitInputDTO(
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate visitDate,
    String healthCareProfId,
    String visitComments,
    String visitSiteId,
    String medicalSalesRepId,
    // products handed out; omitted means none
    @Valid @Size(max = VisitItem.MAX_ITEMS) List<VisitItemDTO> items
) {

    /** Without items — kept for callers that predate them. */
    public CreateVisitInputDTO(LocalDate visitDate, String healthCareProfId, String visitComments, String visitSiteId, String medicalSalesRepId) {
        this(visitDate, healthCareProfId, visitComments, visitSiteId, medicalSalesRepId, null);
    }
}

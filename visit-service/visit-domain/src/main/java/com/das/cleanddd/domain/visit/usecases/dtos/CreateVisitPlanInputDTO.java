package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.LocalDateTime;
import java.util.List;

import com.das.cleanddd.domain.visit.entities.VisitItem;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVisitPlanInputDTO(
    @NotNull
    @Future
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime visitDateTime,
    @NotNull String healthCareProfId,
    String visitComments,
    @NotNull String visitSiteId,
    String medicalSalesRepId, // optional in requests: visit-application sets the caller's own MSR
    // products to bring; omitted means none
    @Valid @Size(max = VisitItem.MAX_ITEMS) List<VisitItemDTO> items
) {

    /** Without items — kept for callers that predate them. */
    public CreateVisitPlanInputDTO(LocalDateTime visitDateTime, String healthCareProfId, String visitComments, String visitSiteId, String medicalSalesRepId) {
        this(visitDateTime, healthCareProfId, visitComments, visitSiteId, medicalSalesRepId, null);
    }
}

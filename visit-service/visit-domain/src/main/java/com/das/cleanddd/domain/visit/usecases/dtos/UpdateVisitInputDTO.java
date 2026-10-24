package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.LocalDate;
import java.util.List;

import com.das.cleanddd.domain.visit.entities.VisitItem;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateVisitInputDTO(
    String id,
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate visitDate,
    String healthCareProfId,
    String visitComments,
    String visitSiteId,
    String medicalSalesRepId,
    // the full new list of products handed out; omitted keeps the current items
    @Valid @Size(max = VisitItem.MAX_ITEMS) List<VisitItemDTO> items,
    // why the record is changed: required, at most 500 characters; kept in the audit trail
    String reason
) {

    /** Without items — kept for callers that predate them. */
    public UpdateVisitInputDTO(String id, LocalDate visitDate, String healthCareProfId, String visitComments, String visitSiteId, String medicalSalesRepId) {
        this(id, visitDate, healthCareProfId, visitComments, visitSiteId, medicalSalesRepId, null, null);
    }

    /** Without a reason, which the update refuses: kept for callers that predate it. */
    public UpdateVisitInputDTO(String id, LocalDate visitDate, String healthCareProfId, String visitComments, String visitSiteId, String medicalSalesRepId, List<VisitItemDTO> items) {
        this(id, visitDate, healthCareProfId, visitComments, visitSiteId, medicalSalesRepId, items, null);
    }
}

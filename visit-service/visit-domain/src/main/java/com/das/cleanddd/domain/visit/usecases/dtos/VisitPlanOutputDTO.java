package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.LocalDateTime;
import java.util.List;

public record VisitPlanOutputDTO(
    String id,
    LocalDateTime visitDateTime,
    String healthCareProfId,
    String visitComments,
    String visitSiteId,
    String medicalSalesRepId,
    boolean active,
    List<VisitItemDTO> items
) {

    /** Without items — kept for callers that predate them. */
    public VisitPlanOutputDTO(String id, LocalDateTime visitDateTime, String healthCareProfId, String visitComments, String visitSiteId, String medicalSalesRepId, boolean active) {
        this(id, visitDateTime, healthCareProfId, visitComments, visitSiteId, medicalSalesRepId, active, List.of());
    }
}

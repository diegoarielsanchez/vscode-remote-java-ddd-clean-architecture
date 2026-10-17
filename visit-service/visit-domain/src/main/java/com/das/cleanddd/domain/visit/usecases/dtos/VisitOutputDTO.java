package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.LocalDateTime;
import java.util.List;

public record VisitOutputDTO(
    String id,
    LocalDateTime visitDate,
    String healthCareProfId,
    String visitComments,
    String visitSiteId,
    String medicalSalesRepId,
    List<AttachmentResultDTO> productPromoAttachments,
    List<VisitItemDTO> items
) {

    /** Without items — kept for callers that predate them. */
    public VisitOutputDTO(String id, LocalDateTime visitDate, String healthCareProfId, String visitComments, String visitSiteId, String medicalSalesRepId, List<AttachmentResultDTO> productPromoAttachments) {
        this(id, visitDate, healthCareProfId, visitComments, visitSiteId, medicalSalesRepId, productPromoAttachments, List.of());
    }
}

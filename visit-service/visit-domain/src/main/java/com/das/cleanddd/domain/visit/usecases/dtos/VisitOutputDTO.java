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
    List<AttachmentResultDTO> productPromoAttachments
) {}

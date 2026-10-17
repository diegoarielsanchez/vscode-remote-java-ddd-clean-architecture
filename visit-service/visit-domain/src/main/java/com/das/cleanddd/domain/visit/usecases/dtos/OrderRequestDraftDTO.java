package com.das.cleanddd.domain.visit.usecases.dtos;

import java.util.List;

/**
 * A body ready for order-service's {@code POST /api/v1/orders/create}, which places the order for
 * the caller's own MSR. Nothing is submitted here.
 */
public record OrderRequestDraftDTO(
    List<VisitItemDTO> lines
) {}

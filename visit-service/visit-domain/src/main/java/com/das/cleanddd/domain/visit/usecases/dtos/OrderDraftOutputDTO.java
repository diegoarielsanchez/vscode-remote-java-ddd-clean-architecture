package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.YearMonth;
import java.util.List;

/**
 * @param plannedVisits the month's active visit plans the draft was built from
 * @param lines         every product the plans bring, with its stock flag
 * @param orderRequests the lines not known to be out of stock, split into ready-to-submit orders of
 *                      at most {@code DraftMonthlyOrdersUseCase.MAX_ORDER_LINES} lines — an order
 *                      with any short line would be rejected whole
 */
public record OrderDraftOutputDTO(
    String medicalSalesRepId,
    YearMonth month,
    int plannedVisits,
    List<OrderDraftLineDTO> lines,
    List<OrderRequestDraftDTO> orderRequests
) {}

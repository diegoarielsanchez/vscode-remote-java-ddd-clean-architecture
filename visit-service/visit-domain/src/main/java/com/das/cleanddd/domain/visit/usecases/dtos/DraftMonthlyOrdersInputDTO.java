package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.YearMonth;

/**
 * @param medicalSalesRepId the MSR whose visit plans to order for
 * @param month             the month of visit plans; {@code null} means next month
 */
public record DraftMonthlyOrdersInputDTO(
    String medicalSalesRepId,
    YearMonth month
) {}

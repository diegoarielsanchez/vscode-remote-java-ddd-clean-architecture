package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.YearMonth;

/**
 * @param medicalSalesRepId the MSR to plan for
 * @param month             the month to plan; {@code null} means next month
 */
public record SuggestVisitPlansInputDTO(
    String medicalSalesRepId,
    YearMonth month
) {}

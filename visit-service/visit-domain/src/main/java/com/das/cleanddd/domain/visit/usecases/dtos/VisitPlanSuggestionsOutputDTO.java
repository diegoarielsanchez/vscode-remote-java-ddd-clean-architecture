package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.YearMonth;
import java.util.List;

/**
 * @param unscheduledHealthCareProfIds HCPs due this month that did not fit in the remaining
 *                                     visit slots — the least overdue are dropped first
 */
public record VisitPlanSuggestionsOutputDTO(
    String medicalSalesRepId,
    YearMonth month,
    List<VisitPlanSuggestionDTO> suggestions,
    List<String> unscheduledHealthCareProfIds
) {}

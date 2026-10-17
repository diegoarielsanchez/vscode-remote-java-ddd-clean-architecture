package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * One proposed visit. Nothing is saved: a rep turns it into a plan through
 * {@code POST /api/v1/visitplan/create} with these values.
 *
 * @param usualIntervalDays the gap between this HCP's past visits that the suggestion assumes
 * @param reason            why this visit is suggested, in plain words
 * @param items             products to bring, from what this HCP usually received; empty when the
 *                          rep recorded no products on past visits
 */
public record VisitPlanSuggestionDTO(
    String healthCareProfId,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime visitDateTime,
    String visitSiteId,
    LocalDate lastVisitDate,
    int usualIntervalDays,
    String reason,
    List<SuggestedVisitItemDTO> items
) {}

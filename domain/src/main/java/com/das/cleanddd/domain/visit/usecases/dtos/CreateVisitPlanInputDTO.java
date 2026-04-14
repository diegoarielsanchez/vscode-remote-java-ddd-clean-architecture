package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

public record CreateVisitPlanInputDTO(
    @NotNull
    @Future
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    LocalDateTime visitDateTime,
    @NotNull String healthCareProfId,
    String visitComments,
    @NotNull String visitSiteId,
    @NotNull String medicalSalesRepId
) {}

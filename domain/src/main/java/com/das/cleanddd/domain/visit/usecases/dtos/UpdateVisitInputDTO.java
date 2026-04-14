package com.das.cleanddd.domain.visit.usecases.dtos;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotNull;

public record UpdateVisitInputDTO(
    String id,
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate visitDate,
    String healthCareProfId,
    String visitComments,
    String visitSiteId,
    String medicalSalesRepId
) {}

package com.das.cleanddd.domain.visit.usecases.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ListVisitsInputDTO(
        @Min(1) int page,
        @Min(1) @Max(100) int pageSize
) {}

package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateProductInputDTO(
    @NotBlank String id,
    @NotBlank String name,
    String description,
    @NotNull BigDecimal price,
    @NotBlank String unit
) {}

package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateProductInputDTO(
  @NotBlank String name,
  String description,
  @NotNull BigDecimal price,
  @NotBlank String unit,
  @Min(0) Integer initialStock
) {}

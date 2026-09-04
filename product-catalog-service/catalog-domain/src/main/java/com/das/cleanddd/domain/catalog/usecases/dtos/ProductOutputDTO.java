package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;

public record ProductOutputDTO(
  String id,
  String name,
  String description,
  BigDecimal price,
  String unit,
  Integer stock,
  Boolean active
) {}

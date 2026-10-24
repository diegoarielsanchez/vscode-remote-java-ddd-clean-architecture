package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;

/** {@code stock} is the units on hand across all lots; {@code gtin} (14 digits) is {@code null} when not on file. */
public record ProductOutputDTO(
  String id,
  String name,
  String description,
  BigDecimal price,
  String unit,
  Integer stock,
  Boolean active,
  String gtin
) {}

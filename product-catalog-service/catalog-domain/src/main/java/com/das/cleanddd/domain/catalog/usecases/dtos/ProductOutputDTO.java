package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

/** {@code batchNumber}, {@code serialNumber} and {@code expirationDate} are {@code null} when not on file. */
public record ProductOutputDTO(
  String id,
  String name,
  String description,
  BigDecimal price,
  String unit,
  Integer stock,
  Boolean active,
  String batchNumber,
  String serialNumber,
  LocalDate expirationDate
) {}

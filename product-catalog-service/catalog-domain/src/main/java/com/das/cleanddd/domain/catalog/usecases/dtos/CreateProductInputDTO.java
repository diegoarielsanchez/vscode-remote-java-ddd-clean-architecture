package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code gtin} is optional. With an {@code initialStock} above zero, {@code batchNumber} and
 * {@code expirationDate} (ISO {@code yyyy-MM-dd}) are required: they describe the first lot.
 */
public record CreateProductInputDTO(
  @NotBlank String name,
  String description,
  @NotNull BigDecimal price,
  @NotBlank String unit,
  @Min(0) Integer initialStock,
  String gtin,
  String batchNumber,
  LocalDate expirationDate
) {

  /** Without a GTIN or a first lot. */
  public CreateProductInputDTO(String name, String description, BigDecimal price, String unit, Integer initialStock) {
    this(name, description, price, unit, initialStock, null, null, null);
  }
}

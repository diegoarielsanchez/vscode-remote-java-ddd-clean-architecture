package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code batchNumber}, {@code serialNumber} and {@code expirationDate} (ISO {@code yyyy-MM-dd}) are optional;
 * their format rules live in the value objects.
 */
public record CreateProductInputDTO(
  @NotBlank String name,
  String description,
  @NotNull BigDecimal price,
  @NotBlank String unit,
  @Min(0) Integer initialStock,
  String batchNumber,
  String serialNumber,
  LocalDate expirationDate
) {

  /** Without batch number, serial number or expiration date. */
  public CreateProductInputDTO(String name, String description, BigDecimal price, String unit, Integer initialStock) {
    this(name, description, price, unit, initialStock, null, null, null);
  }
}

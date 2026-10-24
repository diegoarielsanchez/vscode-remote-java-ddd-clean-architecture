package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code gtin} is optional. {@code lotTracked} defaults to {@code true} (medicines); set it to {@code false} for
 * promotional material held without batches, such as brochures. It cannot be changed later.
 *
 * <p>With an {@code initialStock} above zero, {@code batchNumber} and {@code expirationDate} (ISO
 * {@code yyyy-MM-dd}) describe the first lot: both are required for a lot-tracked product, both optional otherwise
 * (an expiration date then needs a batch number, e.g. the material's approval code).
 */
public record CreateProductInputDTO(
  @NotBlank String name,
  String description,
  @NotNull BigDecimal price,
  @NotBlank String unit,
  @Min(0) Integer initialStock,
  String gtin,
  String batchNumber,
  LocalDate expirationDate,
  Boolean lotTracked
) {

  /** Without a GTIN or a first lot, lot-tracked. */
  public CreateProductInputDTO(String name, String description, BigDecimal price, String unit, Integer initialStock) {
    this(name, description, price, unit, initialStock, null, null, null, null);
  }

  /** Lot-tracked. */
  public CreateProductInputDTO(String name, String description, BigDecimal price, String unit, Integer initialStock,
                               String gtin, String batchNumber, LocalDate expirationDate) {
    this(name, description, price, unit, initialStock, gtin, batchNumber, expirationDate, null);
  }

  public boolean isLotTracked() {
    return !Boolean.FALSE.equals(lotTracked);
  }
}

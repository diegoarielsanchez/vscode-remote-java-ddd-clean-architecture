package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Batch number, serial number and expiration date: {@code null} (or left out) keeps what is on file, so a client
 * that does not know these fields never erases them. An empty {@code batchNumber} / {@code serialNumber}
 * ({@code ""}) removes it. An expiration date can be corrected but not removed.
 */
public record UpdateProductInputDTO(
    @NotBlank String id,
    @NotBlank String name,
    String description,
    @NotNull BigDecimal price,
    @NotBlank String unit,
    String batchNumber,
    String serialNumber,
    LocalDate expirationDate
) {

    /** Keeps batch number, serial number and expiration date as they are. */
    public UpdateProductInputDTO(String id, String name, String description, BigDecimal price, String unit) {
        this(id, name, description, price, unit, null, null, null);
    }
}

package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code gtin}: {@code null} (or left out) keeps the stored one. A product without a GTIN can be given one;
 * once set it cannot be changed or removed (GS1: a GTIN stays with its trade item).
 */
public record UpdateProductInputDTO(
    @NotBlank String id,
    @NotBlank String name,
    String description,
    @NotNull BigDecimal price,
    @NotBlank String unit,
    String gtin
) {

    /** Keeps the GTIN as it is. */
    public UpdateProductInputDTO(String id, String name, String description, BigDecimal price, String unit) {
        this(id, name, description, price, unit, null);
    }
}

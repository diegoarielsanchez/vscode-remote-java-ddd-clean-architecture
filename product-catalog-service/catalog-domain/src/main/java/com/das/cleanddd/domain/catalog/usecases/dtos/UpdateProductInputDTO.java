package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code gtin}: {@code null} (or left out) keeps the stored one. A product without a GTIN can be given one;
 * once set it cannot be changed or removed (GS1: a GTIN stays with its trade item). {@code reason}: why the
 * product is changed (required, at most 500 characters); kept in the audit trail.
 */
public record UpdateProductInputDTO(
    @NotBlank String id,
    @NotBlank String name,
    String description,
    @NotNull BigDecimal price,
    @NotBlank String unit,
    String gtin,
    String reason
) {

    /** Keeps the GTIN as it is; without a reason. */
    public UpdateProductInputDTO(String id, String name, String description, BigDecimal price, String unit) {
        this(id, name, description, price, unit, null, null);
    }

    /** Without a reason, which the update refuses: kept for callers that predate it. */
    public UpdateProductInputDTO(String id, String name, String description, BigDecimal price, String unit,
                                 String gtin) {
        this(id, name, description, price, unit, gtin, null);
    }
}

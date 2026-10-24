package com.das.cleanddd.domain.catalog.usecases.dtos;

/** Identifies one lot: a product and a batch number. {@code reason} is recorded with quarantine changes. */
public record LotInputDTO(
    String productId,
    String batchNumber,
    String reason
) {}

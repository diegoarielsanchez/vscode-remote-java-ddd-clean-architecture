package com.das.cleanddd.domain.catalog.usecases.dtos;

/** A quantity of one lot: units given back to that batch. */
public record LotQuantityInputDTO(
    String productId,
    String batchNumber,
    int quantity
) {}

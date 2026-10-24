package com.das.cleanddd.domain.catalog.usecases.dtos;

/** Shared output shape for release-stock: units on hand across all lots afterwards. */
public record StockQuantityOutputDTO(
    int remainingStock
) {}

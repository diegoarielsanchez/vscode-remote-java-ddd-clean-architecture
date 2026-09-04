package com.das.cleanddd.domain.catalog.usecases.dtos;

/** Shared output shape for release-stock and restock. */
public record StockQuantityOutputDTO(
    int remainingStock
) {}

package com.das.cleanddd.domain.catalog.usecases.dtos;

/** Shared input shape for reserve-stock, release-stock, restock, and availability checks. */
public record StockQuantityInputDTO(
    String productId,
    int quantity
) {}

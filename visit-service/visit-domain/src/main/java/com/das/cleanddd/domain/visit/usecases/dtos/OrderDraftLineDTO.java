package com.das.cleanddd.domain.visit.usecases.dtos;

/**
 * One product to order for the month.
 *
 * @param quantity     units across all of the month's active visit plans
 * @param plannedVisits how many of those plans bring this product
 * @param inStock      whether the catalog has {@code quantity} units on hand; {@code null} when it
 *                     could not be asked
 */
public record OrderDraftLineDTO(
    String productId,
    int quantity,
    int plannedVisits,
    Boolean inStock
) {}

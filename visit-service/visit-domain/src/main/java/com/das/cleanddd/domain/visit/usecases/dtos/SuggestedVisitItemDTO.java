package com.das.cleanddd.domain.visit.usecases.dtos;

/**
 * A product to bring on a suggested visit.
 *
 * @param quantity what this HCP usually received per visit
 * @param inStock  whether the catalog has enough units for this product's total across all of the
 *                 month's suggestions; {@code null} when the catalog could not be asked
 */
public record SuggestedVisitItemDTO(
    String productId,
    int quantity,
    Boolean inStock
) {}

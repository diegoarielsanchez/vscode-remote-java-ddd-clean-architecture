package com.das.cleanddd.domain.visit.usecases.dtos;

import java.util.ArrayList;
import java.util.List;

import com.das.cleanddd.domain.shared.Identifier;
import com.das.cleanddd.domain.visit.entities.VisitItem;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** A product and how many units of it — handed out on a visit, or to bring on a planned one. */
public record VisitItemDTO(
    @NotBlank String productId,
    @Min(1) @Max(VisitItem.MAX_QUANTITY) int quantity
) {

    /** @throws IllegalArgumentException for a blank product or an out-of-range quantity */
    public static List<VisitItem> toEntities(List<VisitItemDTO> items) {
        List<VisitItem> entities = new ArrayList<>();
        if (items != null) {
            for (VisitItemDTO item : items) {
                if (item == null || item.productId() == null || item.productId().isBlank()) {
                    throw new IllegalArgumentException("Visit item product is required.");
                }
                entities.add(new VisitItem(new Identifier(item.productId()) {}, item.quantity()));
            }
        }
        return entities;
    }

    public static List<VisitItemDTO> fromEntities(List<VisitItem> items) {
        return items.stream().map(item -> new VisitItemDTO(item.productId().value(), item.quantity())).toList();
    }
}

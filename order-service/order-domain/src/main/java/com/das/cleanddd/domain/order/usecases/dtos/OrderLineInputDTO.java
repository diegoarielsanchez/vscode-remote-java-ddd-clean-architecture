package com.das.cleanddd.domain.order.usecases.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderLineInputDTO(
    @NotBlank String productId,
    @Min(1) int quantity
) {}

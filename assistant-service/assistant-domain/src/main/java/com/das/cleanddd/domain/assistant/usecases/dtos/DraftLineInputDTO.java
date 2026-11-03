package com.das.cleanddd.domain.assistant.usecases.dtos;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record DraftLineInputDTO(
    @NotBlank String productId,
    @Min(1) int quantity
) {}

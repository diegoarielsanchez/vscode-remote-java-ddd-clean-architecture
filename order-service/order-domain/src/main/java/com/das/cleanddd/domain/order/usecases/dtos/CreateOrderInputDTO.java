package com.das.cleanddd.domain.order.usecases.dtos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record CreateOrderInputDTO(
    @NotBlank String medicalSalesRepId,
    @NotEmpty @Valid List<OrderLineInputDTO> lines
) {}

package com.das.cleanddd.domain.order.usecases.dtos;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateOrderInputDTO(
    @NotBlank String medicalSalesRepId,
    @NotEmpty @Size(max = 20) @Valid List<OrderLineInputDTO> lines
) {}

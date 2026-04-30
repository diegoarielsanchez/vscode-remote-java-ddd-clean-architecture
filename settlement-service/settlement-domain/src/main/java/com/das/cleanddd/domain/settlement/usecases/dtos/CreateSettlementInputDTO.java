package com.das.cleanddd.domain.settlement.usecases.dtos;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSettlementInputDTO(
    @NotBlank String description,
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate settlementDate,
    @NotBlank String medicalSalesRepId,
    @Valid List<CreateInvoiceInputDTO> invoices
) {}

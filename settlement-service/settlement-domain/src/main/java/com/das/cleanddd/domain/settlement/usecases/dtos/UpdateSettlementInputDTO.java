package com.das.cleanddd.domain.settlement.usecases.dtos;

import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateSettlementInputDTO(
    @NotBlank String id,
    @NotBlank String description,
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate settlementDate,
    @Valid List<CreateInvoiceInputDTO> invoices,
    @NotBlank String medicalSalesRepId
) {}

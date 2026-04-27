package com.das.cleanddd.domain.settlement.usecases.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateInvoiceInputDTO(
    @NotBlank String invoiceNumber,
    @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate issueDate,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dueDate,
    @NotNull @PositiveOrZero BigDecimal amount
) {}

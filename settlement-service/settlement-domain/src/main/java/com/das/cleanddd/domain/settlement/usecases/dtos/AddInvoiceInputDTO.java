package com.das.cleanddd.domain.settlement.usecases.dtos;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Input DTO for the {@code AddInvoiceUseCase}.
 *
 * <p>Combines invoice metadata and the digital invoice file in a single operation.
 * The use case adds the invoice to the settlement <em>and</em> persists the physical
 * file atomically: the file is stored first, then the domain state is updated and
 * finally the settlement (with the new invoice and file metadata) is persisted to
 * the database.</p>
 *
 * <p>The {@code content} field carries the raw file bytes and is populated by the
 * controller from the multipart {@code file} part.</p>
 */
public record AddInvoiceInputDTO(
        @NotBlank String settlementId,
        @NotBlank String invoiceNumber,
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd") LocalDate issueDate,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate dueDate,
        @NotNull @PositiveOrZero BigDecimal amount,
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull byte[] content
) {}

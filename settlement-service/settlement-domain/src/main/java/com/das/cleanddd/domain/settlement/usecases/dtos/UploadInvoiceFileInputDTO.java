package com.das.cleanddd.domain.settlement.usecases.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UploadInvoiceFileInputDTO(
    @NotBlank String settlementId,
    @NotBlank String invoiceId,
    @NotBlank String fileName,
    @NotBlank String contentType,
    @NotNull byte[] content
) {}

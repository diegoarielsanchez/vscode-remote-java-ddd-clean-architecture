package com.das.cleanddd.domain.settlement.usecases.dtos;

import jakarta.validation.constraints.NotBlank;

/**
 * Input DTO for the {@code RemoveInvoiceUseCase}.
 *
 * <p>Identifies the settlement and the invoice to remove. The settlement must
 * not be CLOSED; if the invoice has a digital file attached, that file is
 * deleted from storage before the invoice is removed from the domain model.</p>
 */
public record RemoveInvoiceInputDTO(
        @NotBlank String settlementId,
        @NotBlank String invoiceId
) {}

package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.time.LocalDate;

/**
 * Units of one batch arriving into stock. The batch and expiration date come from {@code batchNumber} /
 * {@code expirationDate}, from the pack's GS1 code ({@code gs1}), or from both when they agree.
 */
public record ReceiveStockLotInputDTO(
    String productId,
    int quantity,
    String batchNumber,
    LocalDate expirationDate,
    String gs1
) {}

package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One lot as of today. {@code allocatable}: it can be delivered (available, in stock, and keeping the minimum
 * remaining shelf life). {@code expirationDate} is {@code null} only for stock migrated from before lots existed.
 */
public record StockLotOutputDTO(
    String batchNumber,
    LocalDate expirationDate,
    int quantity,
    String status,
    Instant receivedAt,
    boolean expired,
    boolean allocatable
) {}

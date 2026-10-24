package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;
import java.util.List;

import com.das.cleanddd.domain.catalog.lot.LotAllocation;

/** {@code lots}: the batches the units were taken from, first expiry first out. */
public record ReserveStockOutputDTO(
    boolean reserved,
    int remainingStock,
    BigDecimal unitPrice,
    String productName,
    List<LotAllocation> lots
) {}

package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.math.BigDecimal;

public record ReserveStockOutputDTO(
    boolean reserved,
    int remainingStock,
    BigDecimal unitPrice,
    String productName
) {}

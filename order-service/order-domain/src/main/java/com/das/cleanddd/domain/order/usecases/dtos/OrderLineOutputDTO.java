package com.das.cleanddd.domain.order.usecases.dtos;

import java.math.BigDecimal;

public record OrderLineOutputDTO(
    String productId,
    String productName,
    Integer quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {}

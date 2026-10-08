package com.das.cleanddd.domain.order.usecases.dtos;

import java.math.BigDecimal;
import java.util.List;

/** product-catalog-service's confirmed reservation: one price per reserved product. */
public record ConfirmOrderStockInputDTO(String orderId, List<ReservedLine> lines) {

    public ConfirmOrderStockInputDTO {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record ReservedLine(String productId, String productName, BigDecimal unitPrice) {
    }
}

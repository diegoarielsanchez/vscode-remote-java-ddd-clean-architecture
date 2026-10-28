package com.das.cleanddd.domain.order.usecases.dtos;

import java.math.BigDecimal;
import java.util.List;

import com.das.cleanddd.domain.shared.storage.StorageCondition;

/**
 * product-catalog-service's confirmed reservation: one price per reserved product, and the storage conditions its
 * units must travel under ({@code null}: none).
 */
public record ConfirmOrderStockInputDTO(String orderId, List<ReservedLine> lines) {

    public ConfirmOrderStockInputDTO {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record ReservedLine(String productId, String productName, BigDecimal unitPrice,
                               StorageCondition storageCondition) {

        public ReservedLine(String productId, String productName, BigDecimal unitPrice) {
            this(productId, productName, unitPrice, null);
        }
    }
}

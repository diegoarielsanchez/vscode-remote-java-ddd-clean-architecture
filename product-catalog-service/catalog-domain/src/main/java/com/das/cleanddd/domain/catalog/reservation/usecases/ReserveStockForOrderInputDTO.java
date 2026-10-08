package com.das.cleanddd.domain.catalog.reservation.usecases;

import java.util.List;

/** An order's {@code order.created}: the lines to reserve. */
public record ReserveStockForOrderInputDTO(String orderId, List<Line> lines) {

    public ReserveStockForOrderInputDTO {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record Line(String productId, int quantity) {
    }
}

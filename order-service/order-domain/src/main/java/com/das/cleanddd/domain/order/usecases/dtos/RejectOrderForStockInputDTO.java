package com.das.cleanddd.domain.order.usecases.dtos;

/** product-catalog-service's rejected reservation. */
public record RejectOrderForStockInputDTO(String orderId, String reason) {
}

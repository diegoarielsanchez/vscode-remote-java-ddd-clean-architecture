package com.das.cleanddd.domain.order.ports;

import java.math.BigDecimal;

/**
 * Outbound port to product-catalog-service's mutating stock endpoints.
 * Implemented in infra via a {@code @LoadBalanced RestTemplate} call that
 * forwards the caller's JWT (these endpoints are authenticated, not
 * {@code permitAll} — see product-catalog-service's ProductController for why).
 */
public interface IProductStockPort {

    record StockReservationResult(boolean reserved, int remainingStock, BigDecimal unitPrice, String productName) {}

    StockReservationResult reserve(String productId, int quantity);

    void release(String productId, int quantity);
}

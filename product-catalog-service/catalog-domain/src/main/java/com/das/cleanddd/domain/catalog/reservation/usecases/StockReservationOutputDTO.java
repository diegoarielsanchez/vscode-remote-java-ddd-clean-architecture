package com.das.cleanddd.domain.catalog.reservation.usecases;

/** {@code status} is {@code null} when there is no reservation for the order. */
public record StockReservationOutputDTO(String orderId, String status, String reason) {
}

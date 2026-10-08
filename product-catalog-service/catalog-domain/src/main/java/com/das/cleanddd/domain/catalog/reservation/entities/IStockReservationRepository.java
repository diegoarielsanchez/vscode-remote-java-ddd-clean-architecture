package com.das.cleanddd.domain.catalog.reservation.entities;

import java.util.Optional;

public interface IStockReservationRepository {

    Optional<StockReservation> findByOrderId(String orderId);

    void save(StockReservation reservation);
}

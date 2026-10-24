package com.das.cleanddd.domain.catalog.reservation.entities;

import java.util.List;
import java.util.Optional;

public interface IStockReservationRepository {

    Optional<StockReservation> findByOrderId(String orderId);

    void save(StockReservation reservation);

    /** Every order that was allocated units of the lot, whatever its status since (recall traceability). */
    List<LotRecipient> findRecipientsOfLot(String lotId);
}

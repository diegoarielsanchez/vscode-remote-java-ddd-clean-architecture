package com.das.infra.service.catalog.reservation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockReservationJpaRepository extends JpaRepository<StockReservationEntity, String> {

    /** Order id, reservation status and units taken, for every reservation allocated units of the lot. */
    @Query("SELECT r.orderId, r.status, SUM(a.quantity) FROM StockReservationEntity r JOIN r.allocations a "
            + "WHERE a.lotId = :lotId GROUP BY r.orderId, r.status ORDER BY r.orderId")
    List<Object[]> findRecipientsOfLot(@Param("lotId") String lotId);
}

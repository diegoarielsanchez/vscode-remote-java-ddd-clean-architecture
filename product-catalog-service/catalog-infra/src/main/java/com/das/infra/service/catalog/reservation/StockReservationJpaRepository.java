package com.das.infra.service.catalog.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationJpaRepository extends JpaRepository<StockReservationEntity, String> {
}

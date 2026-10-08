package com.das.infra.service.catalog.reservation;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.das.cleanddd.domain.catalog.reservation.entities.IStockReservationRepository;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationLine;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationStatus;
import com.das.cleanddd.domain.catalog.reservation.entities.StockReservation;

@Repository
public class SQLStockReservationRepository implements IStockReservationRepository {

    private final StockReservationJpaRepository jpa;

    public SQLStockReservationRepository(StockReservationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<StockReservation> findByOrderId(String orderId) {
        return jpa.findById(orderId).map(SQLStockReservationRepository::toDomain);
    }

    @Override
    public void save(StockReservation reservation) {
        StockReservationEntity entity = jpa.findById(reservation.orderId())
                .orElseGet(() -> new StockReservationEntity(reservation.orderId()));
        entity.setStatus(reservation.status().name());
        entity.setReason(reservation.reason());
        entity.setCreatedAt(reservation.createdAt());
        entity.setUpdatedAt(reservation.updatedAt());
        entity.getLines().clear();
        reservation.lines().forEach(l -> entity.getLines().add(
                new StockReservationLineEmbeddable(l.productId(), l.quantity(), l.productName(), l.unitPrice())));
        jpa.save(entity);
    }

    private static StockReservation toDomain(StockReservationEntity e) {
        return new StockReservation(e.getOrderId(), ReservationStatus.valueOf(e.getStatus()),
                e.getLines().stream()
                        .map(l -> new ReservationLine(l.getProductId(), l.getQuantity(), l.getProductName(), l.getUnitPrice()))
                        .toList(),
                e.getReason(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

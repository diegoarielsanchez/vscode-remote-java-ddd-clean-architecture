package com.das.infra.service.catalog.reservation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Repository;

import com.das.infra.service.catalog.StorageConditionEmbeddable;
import com.das.cleanddd.domain.catalog.lot.LotAllocation;
import com.das.cleanddd.domain.catalog.reservation.entities.IStockReservationRepository;
import com.das.cleanddd.domain.catalog.reservation.entities.LotRecipient;
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
        entity.getAllocations().clear();
        for (int lineNo = 0; lineNo < reservation.lines().size(); lineNo++) {
            ReservationLine l = reservation.lines().get(lineNo);
            entity.getLines().add(new StockReservationLineEmbeddable(l.productId(), l.quantity(), l.productName(), l.unitPrice(),
                    StorageConditionEmbeddable.of(l.storageCondition())));
            for (LotAllocation a : l.allocations()) {
                entity.getAllocations().add(new StockReservationAllocationEmbeddable(lineNo, a.lotId(), a.batchNumber(),
                        a.expirationDate(), a.quantity()));
            }
        }
        jpa.save(entity);
    }

    @Override
    public List<LotRecipient> findRecipientsOfLot(String lotId) {
        return jpa.findRecipientsOfLot(lotId).stream()
                .map(r -> new LotRecipient((String) r[0], ReservationStatus.valueOf((String) r[1]), ((Number) r[2]).intValue()))
                .toList();
    }

    private static StockReservation toDomain(StockReservationEntity e) {
        Map<Integer, List<LotAllocation>> byLine = e.getAllocations().stream()
                .collect(Collectors.groupingBy(StockReservationAllocationEmbeddable::getLineNo,
                        Collectors.mapping(a -> new LotAllocation(a.getLotId(), a.getBatchNumber(), a.getExpirationDate(),
                                a.getQuantity()), Collectors.toList())));
        List<ReservationLine> lines = new ArrayList<>();
        for (int lineNo = 0; lineNo < e.getLines().size(); lineNo++) {
            StockReservationLineEmbeddable l = e.getLines().get(lineNo);
            lines.add(new ReservationLine(l.getProductId(), l.getQuantity(), l.getProductName(), l.getUnitPrice(),
                    byLine.getOrDefault(lineNo, List.of()), StorageConditionEmbeddable.toDomain(l.getStorageCondition())));
        }
        return new StockReservation(e.getOrderId(), ReservationStatus.valueOf(e.getStatus()), lines,
                e.getReason(), e.getCreatedAt(), e.getUpdatedAt());
    }
}

package com.das.cleanddd.domain.catalog.reservation.usecases;

import java.time.Clock;
import java.util.Optional;

import com.das.cleanddd.domain.catalog.reservation.entities.IStockReservationRepository;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationStatus;
import com.das.cleanddd.domain.catalog.reservation.entities.StockReservation;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * The order was delivered: its reservation is closed as FULFILLED and can no longer be released.
 * Stock does not change (it left the pool when it was reserved). Idempotent.
 */
public final class FulfilStockReservationUseCase implements UseCase<String, StockReservationOutputDTO> {

    private final IStockReservationRepository reservations;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public FulfilStockReservationUseCase(IStockReservationRepository reservations, UnitOfWork unitOfWork, Clock clock) {
        this.reservations = reservations;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public StockReservationOutputDTO execute(String orderId) throws DomainException {
        if (orderId == null || orderId.isBlank()) {
            throw new DomainException("Order Id is required.");
        }
        return unitOfWork.execute(() -> {
            Optional<StockReservation> existing = reservations.findByOrderId(orderId);
            if (existing.isEmpty()) {
                return new StockReservationOutputDTO(orderId, null, null);
            }
            StockReservation reservation = existing.get();
            if (reservation.status() != ReservationStatus.RESERVED) {
                return ReserveStockForOrderUseCase.output(reservation);
            }
            StockReservation fulfilled = reservation.fulfil(clock.instant());
            reservations.save(fulfilled);
            return ReserveStockForOrderUseCase.output(fulfilled);
        });
    }
}

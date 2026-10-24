package com.das.cleanddd.domain.catalog.reservation.usecases;

import java.time.Clock;
import java.util.Optional;

import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.events.ProductStockReleasedEvent;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.StockAllocator;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.reservation.entities.IStockReservationRepository;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationLine;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationStatus;
import com.das.cleanddd.domain.catalog.reservation.entities.StockReservation;
import com.das.cleanddd.domain.catalog.reservation.ports.IStockReservationEventPublisher;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Saga compensation: the order was rejected, so its reserved stock goes back to the pool, once.
 * Only a RESERVED reservation is released; any other state (or none) is a no-op.
 */
public final class ReleaseStockForOrderUseCase implements UseCase<String, StockReservationOutputDTO> {

    private final IStockLotRepository lots;
    private final StockAllocator allocator;
    private final IStockReservationRepository reservations;
    private final IProductEventPublisher productEvents;
    private final IStockReservationEventPublisher reservationEvents;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ReleaseStockForOrderUseCase(IStockLotRepository lots, StockAllocator allocator,
                                       IStockReservationRepository reservations,
                                       IProductEventPublisher productEvents,
                                       IStockReservationEventPublisher reservationEvents,
                                       UnitOfWork unitOfWork, Clock clock) {
        this.lots = lots;
        this.allocator = allocator;
        this.reservations = reservations;
        this.productEvents = productEvents;
        this.reservationEvents = reservationEvents;
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
            for (ReservationLine line : reservation.lines()) {
                // Reservations made before stock was held in lots have no allocations to give back to;
                // their units are released by hand, per batch (release-stock).
                if (line.allocations().isEmpty()) continue;
                ProductId id = new ProductId(line.productId());
                allocator.giveBack(id, line.allocations());
                productEvents.publish(new ProductStockReleasedEvent(line.productId(), line.quantity(), lots.onHand(id)));
            }
            StockReservation released = reservation.release(clock.instant());
            reservations.save(released);
            released.pullDomainEvents().forEach(reservationEvents::publish);
            return ReserveStockForOrderUseCase.output(released);
        });
    }
}

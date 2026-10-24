package com.das.cleanddd.domain.catalog.reservation.usecases;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;
import com.das.cleanddd.domain.catalog.lot.StockAllocator;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.reservation.entities.IStockReservationRepository;
import com.das.cleanddd.domain.catalog.reservation.ports.IStockReservationEventPublisher;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;

/** The catalog's steps of the order stock saga, driven by {@code order.*} events. */
@Service
public class StockReservationUseCaseFactory {

    private final ReserveStockForOrderUseCase reserve;
    private final ReleaseStockForOrderUseCase release;
    private final FulfilStockReservationUseCase fulfil;

    public StockReservationUseCaseFactory(IProductRepository products, IStockLotRepository lots,
                                          IStockReservationRepository reservations,
                                          IProductEventPublisher productEvents,
                                          IStockReservationEventPublisher reservationEvents, UnitOfWork unitOfWork,
                                          ShelfLifePolicy shelfLifePolicy) {
        Clock clock = Clock.systemUTC();
        StockAllocator allocator = new StockAllocator(lots, shelfLifePolicy, clock);
        this.reserve = new ReserveStockForOrderUseCase(products, lots, allocator, reservations, productEvents,
                reservationEvents, unitOfWork, clock);
        this.release = new ReleaseStockForOrderUseCase(lots, allocator, reservations, productEvents, reservationEvents,
                unitOfWork, clock);
        this.fulfil = new FulfilStockReservationUseCase(reservations, unitOfWork, clock);
    }

    public UseCase<ReserveStockForOrderInputDTO, StockReservationOutputDTO> getReserveStockForOrderUseCase() {
        return reserve;
    }
    public UseCase<String, StockReservationOutputDTO> getReleaseStockForOrderUseCase() {
        return release;
    }
    public UseCase<String, StockReservationOutputDTO> getFulfilStockReservationUseCase() {
        return fulfil;
    }
}

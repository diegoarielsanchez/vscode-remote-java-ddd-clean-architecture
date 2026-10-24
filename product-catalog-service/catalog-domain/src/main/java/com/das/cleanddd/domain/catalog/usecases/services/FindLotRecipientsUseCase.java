package com.das.cleanddd.domain.catalog.usecases.services;

import java.util.List;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.StockLot;
import com.das.cleanddd.domain.catalog.reservation.entities.IStockReservationRepository;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotRecipientOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** Recall: every order that was allocated units of a lot. order-service resolves each order to its MSR. */
public final class FindLotRecipientsUseCase implements UseCase<LotInputDTO, List<LotRecipientOutputDTO>> {

    private final IProductRepository products;
    private final IStockLotRepository lots;
    private final IStockReservationRepository reservations;

    public FindLotRecipientsUseCase(IProductRepository products, IStockLotRepository lots,
                                    IStockReservationRepository reservations) {
        this.products = products;
        this.lots = lots;
        this.reservations = reservations;
    }

    @Override
    public List<LotRecipientOutputDTO> execute(LotInputDTO input) throws DomainException {
        if (input == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        try {
            Product product = ProductUseCaseSupport.requireProduct(products, input.productId());
            StockLot lot = ProductUseCaseSupport.requireLot(lots, product, input.batchNumber());
            return reservations.findRecipientsOfLot(lot.id()).stream()
                    .map(r -> new LotRecipientOutputDTO(r.orderId(), r.status().name(), r.quantity()))
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}

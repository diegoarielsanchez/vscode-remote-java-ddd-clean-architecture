package com.das.cleanddd.domain.catalog.usecases.services;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.events.ProductStockReleasedEvent;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.StockLot;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotQuantityInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Gives manually reserved units back to the batch they came from, so stock never moves between lots. Not
 * idempotent — see order-service's compensation notes.
 */
public class ReleaseProductStockUseCase implements UseCase<LotQuantityInputDTO, StockQuantityOutputDTO> {

    private final IProductRepository repository;
    private final IStockLotRepository lots;
    private final IProductEventPublisher publisher;
    private final UnitOfWork unitOfWork;

    public ReleaseProductStockUseCase(IProductRepository repository, IStockLotRepository lots,
                                      IProductEventPublisher publisher, UnitOfWork unitOfWork) {
        this.repository = repository;
        this.lots = lots;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
    }

    /**
     * Loading, changing, saving and recording the events happen in one unit of work, so the
     * change and its outbox entries commit (or roll back) together.
     */
    @Override
    public StockQuantityOutputDTO execute(LotQuantityInputDTO inputDTO) throws DomainException {
        return unitOfWork.execute(() -> {
            try {
                return doExecute(inputDTO);
            } catch (IllegalArgumentException e) {
                throw new DomainException(e.getMessage());
            }
        });
    }

    private StockQuantityOutputDTO doExecute(LotQuantityInputDTO inputDTO) throws DomainException {
        if (inputDTO.quantity() <= 0) {
            throw new DomainException("Quantity must be greater than zero.");
        }
        Product product = ProductUseCaseSupport.requireProduct(repository, inputDTO.productId());
        StockLot lot = ProductUseCaseSupport.requireLot(lots, product, inputDTO.batchNumber());
        lots.give(product.getId(), lot.id(), inputDTO.quantity());
        int remaining = lots.onHand(product.getId());
        publisher.publish(new ProductStockReleasedEvent(product.getId().value(), inputDTO.quantity(), remaining));
        return new StockQuantityOutputDTO(remaining);
    }
}

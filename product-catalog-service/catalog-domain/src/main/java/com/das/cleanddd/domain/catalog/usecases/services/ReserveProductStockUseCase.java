package com.das.cleanddd.domain.catalog.usecases.services;

import java.util.List;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.events.ProductStockReservedEvent;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.LotAllocation;
import com.das.cleanddd.domain.catalog.lot.StockAllocator;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReserveStockOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Manual reservation (orders reserve through the saga). Units are taken first expiry first out; quarantined lots
 * and lots short of the minimum remaining shelf life are never used. The answer names the lots taken, which are
 * needed to give the units back later.
 */
public class ReserveProductStockUseCase implements UseCase<StockQuantityInputDTO, ReserveStockOutputDTO> {

    private final IProductRepository repository;
    private final IStockLotRepository lots;
    private final StockAllocator allocator;
    private final IProductEventPublisher publisher;
    private final UnitOfWork unitOfWork;

    public ReserveProductStockUseCase(IProductRepository repository, IStockLotRepository lots, StockAllocator allocator,
                                      IProductEventPublisher publisher, UnitOfWork unitOfWork) {
        this.repository = repository;
        this.lots = lots;
        this.allocator = allocator;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
    }

    /**
     * Loading, changing, saving and recording the events happen in one unit of work, so the
     * change and its outbox entries commit (or roll back) together.
     */
    @Override
    public ReserveStockOutputDTO execute(StockQuantityInputDTO inputDTO) throws DomainException {
        return unitOfWork.execute(() -> doExecute(inputDTO));
    }

    private ReserveStockOutputDTO doExecute(StockQuantityInputDTO inputDTO) throws DomainException {
        if (inputDTO.quantity() <= 0) {
            throw new DomainException("Quantity must be greater than zero.");
        }
        Product product = ProductUseCaseSupport.requireProduct(repository, inputDTO.productId());
        List<LotAllocation> taken = allocator.take(product.getId(), inputDTO.quantity());
        if (taken.isEmpty()) {
            throw new DomainException("Insufficient stock for product " + product.getId().value());
        }
        int remaining = lots.onHand(product.getId());
        publisher.publish(new ProductStockReservedEvent(product.getId().value(), inputDTO.quantity(), remaining));
        return new ReserveStockOutputDTO(true, remaining, product.getPrice().value(), product.getName().value(), taken);
    }
}

package com.das.cleanddd.domain.catalog.usecases.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.events.ProductStockReservedEvent;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReserveStockOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Reserves stock via a single atomic conditional UPDATE in the repository
 * (never a load-mutate-save round trip — see {@link IProductRepository#tryReserveStock}),
 * so this use case builds and publishes {@link ProductStockReservedEvent} directly
 * rather than going through {@code Product.pullDomainEvents()}: the in-memory
 * {@link Product} returned here was never mutated through a recording aggregate method.
 */
public class ReserveProductStockUseCase implements UseCase<StockQuantityInputDTO, ReserveStockOutputDTO> {

    @Autowired
    private final IProductRepository repository;
    private final IProductEventPublisher publisher;
    private final UnitOfWork unitOfWork;

    private final Clock clock;

    public ReserveProductStockUseCase(IProductRepository repository, IProductEventPublisher publisher, UnitOfWork unitOfWork) {
        this(repository, publisher, unitOfWork, Clock.systemUTC());
    }

    public ReserveProductStockUseCase(IProductRepository repository, IProductEventPublisher publisher, UnitOfWork unitOfWork,
                                      Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
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
        if (inputDTO.productId() == null) {
            throw new DomainException("Product Id is required.");
        }
        if (inputDTO.quantity() <= 0) {
            throw new DomainException("Quantity must be greater than zero.");
        }
        ProductId id = new ProductId(inputDTO.productId());
        Optional<Product> existing = repository.findById(id);
        if (!existing.isPresent()) {
            throw new DomainException("Product not found.");
        }
        if (existing.get().isExpiredOn(LocalDate.now(clock))) {
            throw new DomainException("Product " + id.value() + " is expired.");
        }

        boolean reserved = repository.tryReserveStock(id, inputDTO.quantity());
        if (!reserved) {
            throw new DomainException("Insufficient stock for product " + id.value());
        }

        Optional<Product> updated = repository.findById(id);
        if (!updated.isPresent()) {
            throw new DomainException("Product not found.");
        }
        Product product = updated.get();
        publisher.publish(new ProductStockReservedEvent(id.value(), inputDTO.quantity(), product.getStock().value()));
        return new ReserveStockOutputDTO(true, product.getStock().value(), product.getPrice().value(), product.getName().value());
    }
}

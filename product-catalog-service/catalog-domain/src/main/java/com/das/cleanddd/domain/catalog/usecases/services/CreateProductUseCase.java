package com.das.cleanddd.domain.catalog.usecases.services;

import java.time.Clock;
import java.time.LocalDate;

import com.das.cleanddd.domain.catalog.entities.Gtin;
import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductDescription;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductStock;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;
import com.das.cleanddd.domain.catalog.events.ProductRestockedEvent;
import com.das.cleanddd.domain.catalog.lot.BatchNumber;
import com.das.cleanddd.domain.catalog.lot.ExpirationDate;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.StockLot;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.CreateProductInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Creates a product, inactive. An {@code initialStock} above zero becomes the product's first lot, so stock is never
 * held without a batch number and expiration date.
 */
public final class CreateProductUseCase implements UseCase<CreateProductInputDTO, ProductOutputDTO> {

    private final IProductRepository repository;
    private final IStockLotRepository lots;
    private final ProductMapper mapper;
    private final IProductEventPublisher publisher;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public CreateProductUseCase(IProductRepository repository, IStockLotRepository lots, ProductMapper mapper,
                                IProductEventPublisher publisher, UnitOfWork unitOfWork, Clock clock) {
        this.repository = repository;
        this.lots = lots;
        this.mapper = mapper;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    /**
     * Loading, changing, saving and recording the events happen in one unit of work, so the
     * change and its outbox entries commit (or roll back) together.
     */
    @Override
    public ProductOutputDTO execute(CreateProductInputDTO inputDTO) throws DomainException {
        return unitOfWork.execute(() -> doExecute(inputDTO));
    }

    private ProductOutputDTO doExecute(CreateProductInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        try {
            // Presence/format rules are enforced by their respective Value Objects; duplicating
            // those checks here would just create a second, divergent source of truth.
            ProductName name = new ProductName(inputDTO.name());
            ProductDescription description = inputDTO.description() == null
                    ? null : new ProductDescription(inputDTO.description());
            ProductPrice price = new ProductPrice(inputDTO.price());
            ProductUnit unit = new ProductUnit(inputDTO.unit());
            Gtin gtin = inputDTO.gtin() == null ? null : new Gtin(inputDTO.gtin());
            int initialStock = inputDTO.initialStock() == null ? 0 : inputDTO.initialStock();
            if (initialStock < 0) {
                throw new DomainException("Stock must be zero or positive.");
            }
            ProductId id = ProductId.random();
            StockLot firstLot = null;
            if (initialStock > 0) {
                firstLot = firstLot(id, inputDTO, initialStock);
            } else if (inputDTO.batchNumber() != null || inputDTO.expirationDate() != null) {
                throw new DomainException("A batch number and expiration date need an initialStock above zero.");
            }
            if (gtin != null) {
                ProductUseCaseSupport.requireUnusedGtin(repository, gtin, null);
            }

            // New products are created inactive — mirrors the MSR/HCP "create
            // inactive, explicit activate step" convention.
            Product product = Product.create(id, name, description, price, unit, new ProductStock(initialStock), gtin);
            repository.save(product);
            product.pullDomainEvents().forEach(publisher::publish);
            if (firstLot != null) {
                lots.save(firstLot);
                publisher.publish(new ProductRestockedEvent(id.value(), initialStock, initialStock,
                        firstLot.batchNumber().value(), firstLot.expirationDateValue()));
            }
            return mapper.outputFromEntity(product);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }

    private StockLot firstLot(ProductId id, CreateProductInputDTO inputDTO, int quantity) throws DomainException {
        if (inputDTO.batchNumber() == null || inputDTO.expirationDate() == null) {
            throw new DomainException("Initial stock needs its batchNumber and expirationDate: stock is held in lots.");
        }
        ExpirationDate expirationDate = new ExpirationDate(inputDTO.expirationDate());
        if (expirationDate.isExpiredOn(LocalDate.now(clock))) {
            throw new DomainException("Expired goods cannot be received (expired on " + expirationDate + ").");
        }
        return StockLot.receive(id, new BatchNumber(inputDTO.batchNumber()), expirationDate, quantity, clock.instant());
    }
}

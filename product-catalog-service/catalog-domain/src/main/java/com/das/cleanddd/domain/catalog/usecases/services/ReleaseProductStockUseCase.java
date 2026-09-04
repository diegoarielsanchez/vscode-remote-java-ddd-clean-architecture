package com.das.cleanddd.domain.catalog.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.events.ProductStockReleasedEvent;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** Returns previously-reserved stock to the pool. Not guaranteed idempotent — see order-service's compensation notes. */
public class ReleaseProductStockUseCase implements UseCase<StockQuantityInputDTO, StockQuantityOutputDTO> {

    @Autowired
    private final IProductRepository repository;
    private final IProductEventPublisher publisher;

    public ReleaseProductStockUseCase(IProductRepository repository, IProductEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Override
    public StockQuantityOutputDTO execute(StockQuantityInputDTO inputDTO) throws DomainException {
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

        repository.releaseStock(id, inputDTO.quantity());

        Optional<Product> updated = repository.findById(id);
        if (!updated.isPresent()) {
            throw new DomainException("Product not found.");
        }
        int remainingStock = updated.get().getStock().value();
        publisher.publish(new ProductStockReleasedEvent(id.value(), inputDTO.quantity(), remainingStock));
        return new StockQuantityOutputDTO(remainingStock);
    }
}

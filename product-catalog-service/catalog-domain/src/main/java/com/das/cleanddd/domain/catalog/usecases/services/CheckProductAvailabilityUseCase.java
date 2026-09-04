package com.das.cleanddd.domain.catalog.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.usecases.dtos.AvailabilityOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** Read-only check — never reserves stock, just reports whether {@code quantity} is currently available. */
public class CheckProductAvailabilityUseCase implements UseCase<StockQuantityInputDTO, AvailabilityOutputDTO> {

    @Autowired
    private final IProductRepository repository;

    public CheckProductAvailabilityUseCase(IProductRepository repository) {
        this.repository = repository;
    }

    @Override
    public AvailabilityOutputDTO execute(StockQuantityInputDTO inputDTO) throws DomainException {
        if (inputDTO.productId() == null) {
            throw new DomainException("Product Id is required.");
        }
        ProductId id = new ProductId(inputDTO.productId());
        Optional<Product> product = repository.findById(id);
        if (!product.isPresent()) {
            throw new DomainException("Product not found.");
        }
        Product p = product.get();
        boolean available = Boolean.TRUE.equals(p.isActive()) && p.getStock().value() >= inputDTO.quantity();
        return new AvailabilityOutputDTO(available);
    }
}

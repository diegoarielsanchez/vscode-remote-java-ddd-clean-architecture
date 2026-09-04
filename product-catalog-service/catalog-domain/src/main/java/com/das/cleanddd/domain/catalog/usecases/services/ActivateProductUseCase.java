package com.das.cleanddd.domain.catalog.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductIDDto;
import com.das.cleanddd.domain.shared.UseCaseOnlyInput;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class ActivateProductUseCase implements UseCaseOnlyInput<ProductIDDto> {

    @Autowired
    private final IProductRepository repository;
    private final IProductEventPublisher publisher;

    public ActivateProductUseCase(IProductRepository repository, IProductEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    @Override
    public void execute(ProductIDDto inputDTO) throws DomainException {
        if (inputDTO.productId() == null) {
            throw new DomainException("Product Id is required.");
        }
        ProductId id = new ProductId(inputDTO.productId());
        Optional<Product> product = repository.findById(id);
        if (!product.isPresent()) {
            throw new DomainException("Product not found.");
        }
        if (Boolean.FALSE.equals(product.get().isActive())) {
            Product activated = product.get().setActivate();
            repository.save(activated);
            activated.pullDomainEvents().forEach(publisher::publish);
        }
    }
}

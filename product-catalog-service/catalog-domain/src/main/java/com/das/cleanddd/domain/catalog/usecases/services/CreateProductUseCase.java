package com.das.cleanddd.domain.catalog.usecases.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductDescription;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductStock;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.CreateProductInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

@Service
public final class CreateProductUseCase implements UseCase<CreateProductInputDTO, ProductOutputDTO> {

    @Autowired
    private final IProductRepository repository;
    @Autowired
    private final ProductMapper mapper;
    private final IProductEventPublisher publisher;

    public CreateProductUseCase(IProductRepository repository, ProductMapper mapper, IProductEventPublisher publisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    @Override
    public ProductOutputDTO execute(CreateProductInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        Product product;
        try {
            // Presence/format rules are enforced by their respective Value Objects
            // (ProductName, ProductPrice, ProductUnit, ProductStock); duplicating
            // those checks here would just create a second, divergent source of
            // truth for the same invariant.
            ProductName name = new ProductName(inputDTO.name());
            ProductDescription description = inputDTO.description() == null
                    ? null : new ProductDescription(inputDTO.description());
            ProductPrice price = new ProductPrice(inputDTO.price());
            ProductUnit unit = new ProductUnit(inputDTO.unit());
            ProductStock stock = new ProductStock(inputDTO.initialStock() == null ? 0 : inputDTO.initialStock());

            // New products are created inactive — mirrors the MSR/HCP "create
            // inactive, explicit activate step" convention.
            product = Product.create(null, name, description, price, unit, stock);
            repository.save(product);
            product.pullDomainEvents().forEach(publisher::publish);
            return mapper.outputFromEntity(product);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}

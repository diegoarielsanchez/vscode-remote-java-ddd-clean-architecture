package com.das.cleanddd.domain.catalog.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductDescription;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.UpdateProductInputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

@Service
public final class UpdateProductUseCase implements UseCase<UpdateProductInputDTO, ProductOutputDTO> {

    @Autowired
    private final IProductRepository repository;
    @Autowired
    private final ProductMapper mapper;
    private final IProductEventPublisher publisher;

    public UpdateProductUseCase(IProductRepository repository, ProductMapper mapper, IProductEventPublisher publisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
    }

    @Override
    public ProductOutputDTO execute(UpdateProductInputDTO inputDTO) throws DomainException {
        if (inputDTO == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        Product product;
        try {
            ProductName name = new ProductName(inputDTO.name());
            ProductDescription description = inputDTO.description() == null
                    ? null : new ProductDescription(inputDTO.description());
            ProductPrice price = new ProductPrice(inputDTO.price());
            ProductUnit unit = new ProductUnit(inputDTO.unit());
            ProductId id = new ProductId(inputDTO.id());

            Optional<Product> existing = repository.findById(id);
            if (!existing.isPresent()) {
                throw new DomainException("Product not found.");
            }
            // Never touches stock — stock is only ever mutated via the atomic
            // reserve/release/restock repository operations.
            product = existing.get().withUpdatedDetails(name, description, price, unit);
            repository.save(product);
            product.pullDomainEvents().forEach(publisher::publish);
            return mapper.outputFromEntity(product);
        } catch (IllegalArgumentException e) {
            throw new DomainException(e.getMessage());
        }
    }
}

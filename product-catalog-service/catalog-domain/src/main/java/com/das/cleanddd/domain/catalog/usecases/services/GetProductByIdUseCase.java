package com.das.cleanddd.domain.catalog.usecases.services;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductIDDto;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class GetProductByIdUseCase implements UseCase<ProductIDDto, ProductOutputDTO> {

    @Autowired
    private final IProductRepository repository;
    @Autowired
    private final ProductMapper mapper;

    public GetProductByIdUseCase(IProductRepository repository, ProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public ProductOutputDTO execute(ProductIDDto inputDTO) throws DomainException {
        if (inputDTO.productId() == null) {
            throw new DomainException("Product Id is required.");
        }
        ProductId id = new ProductId(inputDTO.productId());
        Optional<Product> product = repository.findById(id);
        if (!product.isPresent()) {
            throw new DomainException("Product not found.");
        }
        return mapper.outputFromEntity(product.get());
    }
}

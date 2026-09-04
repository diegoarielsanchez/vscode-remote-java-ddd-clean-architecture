package com.das.cleanddd.domain.catalog.usecases.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductNamesInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

public class FindProductsByNameUseCase implements UseCase<ProductNamesInputDTO, List<ProductOutputDTO>> {

    @Autowired
    private final IProductRepository repository;
    @Autowired
    private final ProductMapper mapper;

    public FindProductsByNameUseCase(IProductRepository repository, ProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public List<ProductOutputDTO> execute(ProductNamesInputDTO inputDTO) throws DomainException {
        ProductName name = null;
        if (inputDTO == null) {
            inputDTO = new ProductNamesInputDTO("", 1, 10);
        }
        if (inputDTO.page() <= 0) {
            inputDTO = new ProductNamesInputDTO(inputDTO.name(), 1, inputDTO.pageSize());
        }
        if (inputDTO.pageSize() <= 0) {
            inputDTO = new ProductNamesInputDTO(inputDTO.name(), inputDTO.page(), 10);
        }
        if (inputDTO.name() == null) {
            inputDTO = new ProductNamesInputDTO("", inputDTO.page(), inputDTO.pageSize());
        } else if (!inputDTO.name().isEmpty()) {
            name = new ProductName(inputDTO.name());
        }

        List<Product> products = name == null
                ? repository.searchAll()
                : repository.findByName(name, inputDTO.page(), inputDTO.pageSize());
        if (products.isEmpty()) {
            throw new DomainException("Product not found.");
        }
        return mapper.outputFromEntityList(products);
    }
}

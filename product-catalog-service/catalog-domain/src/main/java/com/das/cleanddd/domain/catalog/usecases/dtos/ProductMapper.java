package com.das.cleanddd.domain.catalog.usecases.dtos;

import java.util.List;

import org.springframework.stereotype.Service;

import com.das.cleanddd.domain.catalog.entities.Product;

@Service
public class ProductMapper {

    public ProductOutputDTO outputFromEntity(Product product) {
        return new ProductOutputDTO(
            product.getId().value(),
            product.getName().value(),
            product.getDescription() == null ? null : product.getDescription().value(),
            product.getPrice().value(),
            product.getUnit().value(),
            product.getStock().value(),
            product.getActive().value()
        );
    }

    public List<ProductOutputDTO> outputFromEntityList(List<Product> products) {
        return products.stream()
            .map(this::outputFromEntity)
            .toList();
    }
}

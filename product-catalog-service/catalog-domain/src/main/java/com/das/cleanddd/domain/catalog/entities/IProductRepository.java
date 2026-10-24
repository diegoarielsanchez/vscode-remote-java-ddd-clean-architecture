package com.das.cleanddd.domain.catalog.entities;

import java.util.List;
import java.util.Optional;

import com.das.cleanddd.domain.shared.criteria.Criteria;

public interface IProductRepository {

    void save(Product product);

    Optional<Product> findById(ProductId id);

    List<Product> findByName(ProductName name, int page, int pageSize);

    List<Product> matching(Criteria criteria);

    List<Product> searchAll();

    /** The product carrying this GTIN, if any: a GTIN identifies one trade item, so at most one product. */
    Optional<Product> findByGtin(Gtin gtin);
}

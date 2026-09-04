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

    /**
     * Atomically decrements stock by {@code quantity} only if enough stock is
     * available (a single conditional {@code UPDATE ... WHERE stock >= quantity}
     * in the infra implementation — never a load-mutate-save round trip, which
     * would leave a race window under concurrent ordering).
     *
     * @return {@code true} if the reservation succeeded, {@code false} if there
     *         was not enough stock (nothing is changed in that case).
     */
    boolean tryReserveStock(ProductId id, int quantity);

    /** Atomically increments stock by {@code quantity} (returns previously-reserved stock to the pool). */
    void releaseStock(ProductId id, int quantity);

    /** Atomically increments stock by {@code quantity} (manual/admin inventory top-up). */
    void restock(ProductId id, int quantity);
}

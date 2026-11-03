package com.das.cleanddd.domain.assistant.ports;

import java.util.List;
import java.util.Optional;

/**
 * Reads from product-catalog-service, made as the calling rep. Implemented in infra; the assistant has no access to
 * the catalog's data other than through its API.
 */
public interface ICatalogPort {

    List<ProductView> findProductsByName(String name, int page);

    Optional<ProductView> findProduct(String productId);

    /** Whether the product is active and its deliverable lots hold at least {@code quantity}. */
    boolean isAvailable(String productId, int quantity);
}

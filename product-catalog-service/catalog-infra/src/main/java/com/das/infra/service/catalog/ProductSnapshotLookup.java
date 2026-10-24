package com.das.infra.service.catalog;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.das.infra.service.catalog.lot.StockLotJpaRepository;

/**
 * Cached read behind {@link SQLProductRepository#findById}. A separate bean because Spring's cache
 * proxy does not intercept calls a class makes to itself.
 */
@Component
public class ProductSnapshotLookup {

    private final ProductJpaRepository jpaRepository;
    private final StockLotJpaRepository lots;

    public ProductSnapshotLookup(ProductJpaRepository jpaRepository, StockLotJpaRepository lots) {
        this.jpaRepository = jpaRepository;
        this.lots = lots;
    }

    /**
     * Only a found product is cached ({@code unless}), so a "not found" lookup — e.g. right after
     * creation, before the id exists — is never memoized past the moment it becomes valid.
     */
    @Cacheable(cacheNames = ProductSnapshot.CACHE, key = "#id", unless = "#result == null")
    public ProductSnapshot find(String id) {
        return jpaRepository.findById(id).map(e -> ProductSnapshot.from(e, lots.onHand(id))).orElse(null);
    }
}

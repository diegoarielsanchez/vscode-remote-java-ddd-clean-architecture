package com.das.infra.service.catalog.lot;

import java.util.List;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;

import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.lot.BatchNumber;
import com.das.cleanddd.domain.catalog.lot.ExpirationDate;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.LotStatus;
import com.das.cleanddd.domain.catalog.lot.StockLot;
import com.das.infra.service.catalog.ProductSnapshot;

/**
 * Every change to a lot changes the product's stock on hand, so each one evicts the cached product, the same way the
 * product's own writes do. Not {@code final}: the cache annotations need a CGLIB proxy.
 */
@Repository
public class SQLStockLotRepository implements IStockLotRepository {

    private final StockLotJpaRepository jpa;

    public SQLStockLotRepository(StockLotJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<StockLot> findByProductId(ProductId productId) {
        return jpa.findByProductId(productId.value()).stream().map(SQLStockLotRepository::toDomain).toList();
    }

    @Override
    public Optional<StockLot> findByProductIdAndBatchNumber(ProductId productId, BatchNumber batchNumber) {
        return jpa.findByProductIdAndBatchNumber(productId.value(), batchNumber.value()).map(SQLStockLotRepository::toDomain);
    }

    @Override
    @CacheEvict(cacheNames = ProductSnapshot.CACHE, key = "#lot.productId().value()")
    public void save(StockLot lot) {
        Optional<StockLotEntity> existing = jpa.findById(lot.id());
        if (existing.isPresent()) {
            existing.get().setStatus(lot.status().name()); // quantity is never written back
            jpa.save(existing.get());
        } else {
            jpa.save(new StockLotEntity(lot.id(), lot.productId().value(), lot.batchNumber().value(),
                    lot.expirationDateValue(), lot.quantity(), lot.status().name(), lot.receivedAt()));
        }
    }

    @Override
    @CacheEvict(cacheNames = ProductSnapshot.CACHE, key = "#lot.productId().value()")
    public boolean tryTake(StockLot lot, int quantity) {
        return jpa.take(lot.id(), quantity) == 1;
    }

    @Override
    @CacheEvict(cacheNames = ProductSnapshot.CACHE, key = "#productId.value()")
    public void give(ProductId productId, String lotId, int quantity) {
        if (jpa.give(lotId, quantity) != 1) {
            throw new IllegalStateException("Stock lot " + lotId + " does not exist.");
        }
    }

    @Override
    public int onHand(ProductId productId) {
        return jpa.onHand(productId.value());
    }

    private static StockLot toDomain(StockLotEntity e) {
        return new StockLot(e.getId(), new ProductId(e.getProductId()), new BatchNumber(e.getBatchNumber()),
                e.getExpirationDate() == null ? null : new ExpirationDate(e.getExpirationDate()),
                e.getQuantity(), LotStatus.valueOf(e.getStatus()), e.getReceivedAt());
    }
}

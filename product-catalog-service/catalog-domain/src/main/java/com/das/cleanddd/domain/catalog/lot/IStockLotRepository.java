package com.das.cleanddd.domain.catalog.lot;

import java.util.List;
import java.util.Optional;

import com.das.cleanddd.domain.catalog.entities.ProductId;

public interface IStockLotRepository {

    List<StockLot> findByProductId(ProductId productId);

    Optional<StockLot> findByProductIdAndBatchNumber(ProductId productId, BatchNumber batchNumber);

    /** Inserts a new lot with its quantity, or stores a status change. Never writes the quantity of an existing lot. */
    void save(StockLot lot);

    /**
     * Atomically takes {@code quantity} units from the lot, only if it is still AVAILABLE and holds enough (a single
     * conditional update, never load-mutate-save).
     *
     * @return {@code false}, changing nothing, when it no longer can
     */
    boolean tryTake(StockLot lot, int quantity);

    /** Atomically adds {@code quantity} units to the lot (receipt of more of the batch, or units given back). */
    void give(ProductId productId, String lotId, int quantity);

    /** Units on hand across all of the product's lots, quarantined ones included and rejected ones left out. */
    int onHand(ProductId productId);
}

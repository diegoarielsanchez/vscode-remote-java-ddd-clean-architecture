package com.das.cleanddd.domain.catalog.lot;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.das.cleanddd.domain.catalog.entities.ProductId;

/**
 * Takes stock for an order from a product's lots, first expiry first out (FEFO). Quarantined lots and lots that
 * would not keep the minimum remaining shelf life are skipped. All or nothing: if the allocatable lots cannot cover
 * the quantity, nothing is taken.
 *
 * <p>Each lot is taken with the repository's atomic conditional update. If another order empties a lot in between,
 * what was taken is given back and the plan is made again from fresh data.
 */
public final class StockAllocator {

    private static final int ATTEMPTS = 3;

    private final IStockLotRepository lots;
    private final ShelfLifePolicy policy;
    private final Clock clock;

    public StockAllocator(IStockLotRepository lots, ShelfLifePolicy policy, Clock clock) {
        this.lots = lots;
        this.policy = policy;
        this.clock = clock;
    }

    /** @return the units taken per lot, or an empty list when there is not enough allocatable stock */
    public List<LotAllocation> take(ProductId productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            List<StockLot> candidates = allocatableLots(productId);
            if (candidates.stream().mapToInt(StockLot::quantity).sum() < quantity) {
                return List.of();
            }
            List<LotAllocation> taken = new ArrayList<>();
            int missing = quantity;
            boolean raced = false;
            for (StockLot lot : candidates) {
                int units = Math.min(missing, lot.quantity());
                if (!lots.tryTake(lot, units)) {
                    raced = true;
                    break;
                }
                taken.add(new LotAllocation(lot.id(), lot.batchNumber().value(), lot.expirationDateValue(), units));
                missing -= units;
                if (missing == 0) {
                    return List.copyOf(taken);
                }
            }
            giveBack(productId, taken);
            if (!raced) {
                return List.of();
            }
        }
        return List.of();
    }

    public void giveBack(ProductId productId, List<LotAllocation> allocations) {
        allocations.forEach(a -> lots.give(productId, a.lotId(), a.quantity()));
    }

    /** Units that could be allocated today. */
    public int allocatable(ProductId productId) {
        return allocatableLots(productId).stream().mapToInt(StockLot::quantity).sum();
    }

    /** The product's lots that may be allocated today, in FEFO order. */
    public List<StockLot> allocatableLots(ProductId productId) {
        LocalDate today = LocalDate.now(clock);
        return lots.findByProductId(productId).stream()
                .filter(l -> l.isAllocatable(today, policy))
                .sorted(StockLot.FEFO)
                .toList();
    }
}

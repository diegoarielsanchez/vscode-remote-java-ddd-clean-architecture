package com.das.cleanddd.domain.catalog.lot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

import com.das.cleanddd.domain.catalog.entities.ProductId;

/**
 * Units of one product from one manufacturing batch. Under GS1 a lot is identified by GTIN + batch number, so a
 * product has at most one lot per batch number, and that lot has one expiration date. A product's stock on hand
 * is the sum of its lots.
 *
 * <p>A product that is not lot-tracked (promotional material) may hold units without a batch: they go into its
 * {@link #UNBATCHED} lot, which has no expiration date.
 *
 * <p>Quantity only changes through the repository's atomic take/give operations, never by saving a copy.
 */
public final class StockLot {

    /** First expiry, first out: earliest expiration first, lots without a date last, then oldest receipt. */
    public static final Comparator<StockLot> FEFO = Comparator
            .comparing((StockLot l) -> l.expirationDate == null ? null : l.expirationDate.value(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(StockLot::receivedAt)
            .thenComparing(l -> l.batchNumber.value());

    /** Batch of the units of a non-lot-tracked product received without one. Lot-tracked products cannot use it. */
    public static final BatchNumber UNBATCHED = new BatchNumber("UNBATCHED");

    private final String id;
    private final ProductId productId;
    private final BatchNumber batchNumber;
    private final ExpirationDate expirationDate;
    private final int quantity;
    private final LotStatus status;
    private final Instant receivedAt;

    public StockLot(String id, ProductId productId, BatchNumber batchNumber, ExpirationDate expirationDate,
                    int quantity, LotStatus status, Instant receivedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.productId = Objects.requireNonNull(productId, "productId");
        this.batchNumber = Objects.requireNonNull(batchNumber, "batchNumber");
        this.expirationDate = expirationDate;
        if (quantity < 0) {
            throw new IllegalArgumentException("Lot quantity cannot be negative.");
        }
        this.quantity = quantity;
        this.status = Objects.requireNonNull(status, "status");
        this.receivedAt = Objects.requireNonNull(receivedAt, "receivedAt");
    }

    /**
     * A newly received lot. {@code expirationDate} is {@code null} only for a product that is not lot-tracked; the
     * receiving use case enforces which product may omit it.
     */
    public static StockLot receive(ProductId productId, BatchNumber batchNumber, ExpirationDate expirationDate,
                                   int quantity, Instant now) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        return new StockLot(UUID.randomUUID().toString(), productId, batchNumber, expirationDate, quantity,
                LotStatus.AVAILABLE, now);
    }

    public StockLot withStatus(LotStatus newStatus) {
        return new StockLot(id, productId, batchNumber, expirationDate, quantity, newStatus, receivedAt);
    }

    public boolean isExpiredOn(LocalDate today) {
        return expirationDate != null && expirationDate.isExpiredOn(today);
    }

    /** Can units of this lot be allocated to an order today? */
    public boolean isAllocatable(LocalDate today, ShelfLifePolicy policy) {
        return status == LotStatus.AVAILABLE && quantity > 0 && policy.allows(expirationDate, today);
    }

    public String id() { return id; }
    public ProductId productId() { return productId; }
    public BatchNumber batchNumber() { return batchNumber; }
    public ExpirationDate expirationDate() { return expirationDate; }
    public LocalDate expirationDateValue() { return expirationDate == null ? null : expirationDate.value(); }
    public int quantity() { return quantity; }
    public LotStatus status() { return status; }
    public Instant receivedAt() { return receivedAt; }
}

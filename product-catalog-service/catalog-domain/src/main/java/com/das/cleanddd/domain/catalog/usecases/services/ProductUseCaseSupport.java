package com.das.cleanddd.domain.catalog.usecases.services;

import java.time.LocalDate;
import java.util.Optional;

import com.das.cleanddd.domain.catalog.entities.Gtin;
import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.lot.BatchNumber;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;
import com.das.cleanddd.domain.catalog.lot.StockLot;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockLotOutputDTO;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** Checks and mappings shared by the product and lot use cases. */
final class ProductUseCaseSupport {

    private ProductUseCaseSupport() {}

    /** GS1: one GTIN identifies one trade item, so it can belong to one product only. */
    static void requireUnusedGtin(IProductRepository products, Gtin gtin, ProductId owner) throws DomainException {
        Optional<Product> holder = products.findByGtin(gtin);
        if (holder.isPresent() && !holder.get().getId().equals(owner)) {
            throw new DomainException("GTIN " + gtin.value() + " already identifies product " + holder.get().getId().value() + ".");
        }
    }

    static Product requireProduct(IProductRepository products, String productId) throws DomainException {
        if (productId == null) {
            throw new DomainException("Product Id is required.");
        }
        return products.findById(new ProductId(productId))
                .orElseThrow(() -> new DomainException("Product not found."));
    }

    /** Without a batch number, a product that is not lot-tracked means its {@link StockLot#UNBATCHED} lot. */
    static StockLot requireLot(IStockLotRepository lots, Product product, String batchNumber) throws DomainException {
        if (batchNumber == null && product.isLotTracked()) {
            throw new DomainException("Batch number is required.");
        }
        BatchNumber batch = batchNumber == null ? StockLot.UNBATCHED : new BatchNumber(batchNumber);
        return lots.findByProductIdAndBatchNumber(product.getId(), batch)
                .orElseThrow(() -> new DomainException("Batch " + batch.value() + " is not on file for this product."));
    }

    /**
     * The batch that received units go into.
     *
     * <ul>
     *   <li>Lot-tracked products (medicines): batch number and expiration date are both required.</li>
     *   <li>Other products (promotional material): both are optional. Units without a batch go into the
     *       {@link StockLot#UNBATCHED} lot. An expiration date, such as the end of a material's approval, needs a
     *       batch number (e.g. its approval code), since one batch has one expiration date.</li>
     * </ul>
     *
     * @param batchHint  how the caller can supply a missing batch number, appended to the message
     * @param expiryHint how the caller can supply a missing expiration date, appended to the message
     */
    static BatchNumber receivingBatch(boolean lotTracked, BatchNumber batchNumber, LocalDate expiry,
                                      String batchHint, String expiryHint) throws DomainException {
        if (StockLot.UNBATCHED.equals(batchNumber) && (lotTracked || expiry != null)) {
            throw new DomainException("Batch " + StockLot.UNBATCHED.value()
                    + " is reserved for units of a product that is not lot-tracked, received without a batch.");
        }
        if (lotTracked) {
            if (batchNumber == null) {
                throw new DomainException("Batch number is required" + batchHint + ".");
            }
            if (expiry == null) {
                throw new DomainException("Expiration date is required" + expiryHint + ".");
            }
            return batchNumber;
        }
        if (batchNumber == null && expiry != null) {
            throw new DomainException("An expiration date needs a batch number (e.g. the material's approval code).");
        }
        return batchNumber == null ? StockLot.UNBATCHED : batchNumber;
    }

    /** The reason recorded with a lot's status change: required, at most 500 characters. */
    static void requireLotReason(String reason) throws DomainException {
        if (reason == null || reason.isBlank()) {
            throw new DomainException("A reason is required.");
        }
        if (reason.length() > 500) {
            throw new DomainException("The reason must be at most 500 characters.");
        }
    }

    /** A rejected lot is final: no units go into it and its status does not change again. */
    static void requireNotRejected(StockLot lot) throws DomainException {
        if (lot.isRejected()) {
            throw new DomainException("Batch " + lot.batchNumber().value() + " was rejected; it cannot be received"
                    + " into, quarantined or released again.");
        }
    }

    static StockLotOutputDTO output(StockLot lot, LocalDate today, ShelfLifePolicy policy) {
        return new StockLotOutputDTO(lot.batchNumber().value(), lot.expirationDateValue(), lot.quantity(),
                lot.status().name(), lot.receivedAt(), lot.isExpiredOn(today), lot.isAllocatable(today, policy));
    }
}

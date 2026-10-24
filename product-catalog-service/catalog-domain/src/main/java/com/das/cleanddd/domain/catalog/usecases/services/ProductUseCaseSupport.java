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

    static StockLot requireLot(IStockLotRepository lots, Product product, String batchNumber) throws DomainException {
        if (batchNumber == null) {
            throw new DomainException("Batch number is required.");
        }
        return lots.findByProductIdAndBatchNumber(product.getId(), new BatchNumber(batchNumber))
                .orElseThrow(() -> new DomainException("Batch " + batchNumber + " is not on file for this product."));
    }

    static StockLotOutputDTO output(StockLot lot, LocalDate today, ShelfLifePolicy policy) {
        return new StockLotOutputDTO(lot.batchNumber().value(), lot.expirationDateValue(), lot.quantity(),
                lot.status().name(), lot.receivedAt(), lot.isExpiredOn(today), lot.isAllocatable(today, policy));
    }
}

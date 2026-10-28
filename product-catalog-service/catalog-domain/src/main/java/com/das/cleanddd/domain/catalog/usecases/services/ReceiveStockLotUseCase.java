package com.das.cleanddd.domain.catalog.usecases.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.events.ProductRestockedEvent;
import com.das.cleanddd.domain.catalog.lot.BatchNumber;
import com.das.cleanddd.domain.catalog.lot.ExpirationDate;
import com.das.cleanddd.domain.catalog.lot.Gs1ElementString;
import com.das.cleanddd.domain.catalog.lot.Gs1ElementString.Gs1Data;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;
import com.das.cleanddd.domain.catalog.lot.StockLot;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReceiveStockLotInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockLotOutputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Goods receipt: units of one batch come into stock.
 *
 * <ul>
 *   <li>The batch number and expiration date can be typed, read from the pack's GS1 code, or both; when both are
 *       given they must agree. A lot-tracked product needs both; any other product may have neither (see
 *       {@link ProductUseCaseSupport#receivingBatch}).</li>
 *   <li>A GS1 code's GTIN must be the product's GTIN, so the wrong item cannot be booked in.</li>
 *   <li>Expired goods are refused.</li>
 *   <li>A batch already on file gets the units added, provided the expiration date is the same: one batch has
 *       one expiration date. A new batch becomes a new lot. A rejected batch takes no more units.</li>
 *   <li>A GS1 serial number (AI 21) is accepted in the code but not stored: stock is tracked per lot.</li>
 * </ul>
 */
public final class ReceiveStockLotUseCase implements UseCase<ReceiveStockLotInputDTO, StockLotOutputDTO> {

    private final IProductRepository products;
    private final IStockLotRepository lots;
    private final IProductEventPublisher publisher;
    private final UnitOfWork unitOfWork;
    private final ShelfLifePolicy policy;
    private final Clock clock;

    public ReceiveStockLotUseCase(IProductRepository products, IStockLotRepository lots, IProductEventPublisher publisher,
                                  UnitOfWork unitOfWork, ShelfLifePolicy policy, Clock clock) {
        this.products = products;
        this.lots = lots;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public StockLotOutputDTO execute(ReceiveStockLotInputDTO input) throws DomainException {
        return unitOfWork.execute(() -> {
            try {
                return doExecute(input);
            } catch (IllegalArgumentException e) {
                throw new DomainException(e.getMessage());
            }
        });
    }

    private StockLotOutputDTO doExecute(ReceiveStockLotInputDTO input) throws DomainException {
        if (input == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        if (input.quantity() <= 0) {
            throw new DomainException("Quantity must be greater than zero.");
        }
        LocalDate today = LocalDate.now(clock);
        Gs1Data scanned = input.gs1() == null ? null : Gs1ElementString.parse(input.gs1(), today);
        BatchNumber typedOrScanned = agree("batchNumber",
                input.batchNumber() == null ? null : new BatchNumber(input.batchNumber()),
                scanned == null ? null : scanned.batchNumber());
        LocalDate expiry = agree("expirationDate", input.expirationDate(), scanned == null ? null : scanned.expirationDate());

        Product product = ProductUseCaseSupport.requireProduct(products, input.productId());
        BatchNumber batchNumber = ProductUseCaseSupport.receivingBatch(product.isLotTracked(), typedOrScanned, expiry,
                ": give batchNumber or a GS1 code with (10)", ": give expirationDate or a GS1 code with (17)");
        ExpirationDate expirationDate = expiry == null ? null : new ExpirationDate(expiry);
        if (scanned != null && scanned.gtin() != null && !scanned.gtin().equals(product.getGtin())) {
            throw new DomainException(product.getGtin() == null
                    ? "This product has no GTIN on file, so a GS1 code with (01) cannot be matched to it."
                    : "The GS1 code is for GTIN " + scanned.gtin().value() + ", not this product's GTIN "
                            + product.getGtin().value() + ".");
        }
        if (expirationDate != null && expirationDate.isExpiredOn(today)) {
            throw new DomainException("Expired goods cannot be received (expired on " + expirationDate + ").");
        }

        Optional<StockLot> existing = lots.findByProductIdAndBatchNumber(product.getId(), batchNumber);
        if (existing.isPresent()) {
            StockLot lot = existing.get();
            ProductUseCaseSupport.requireNotRejected(lot);
            if (!Objects.equals(expirationDate, lot.expirationDate())) {
                throw new DomainException("Batch " + batchNumber.value() + " is on file with expiration date "
                        + lot.expirationDateValue() + "; one batch has one expiration date.");
            }
            lots.give(product.getId(), lot.id(), input.quantity());
        } else {
            lots.save(StockLot.receive(product.getId(), batchNumber, expirationDate, input.quantity(), clock.instant()));
        }

        int onHand = lots.onHand(product.getId());
        publisher.publish(new ProductRestockedEvent(product.getId().value(), input.quantity(), onHand,
                batchNumber.value(), expiry));
        StockLot lot = lots.findByProductIdAndBatchNumber(product.getId(), batchNumber)
                .orElseThrow(() -> new DomainException("Batch " + batchNumber.value() + " was not stored."));
        return ProductUseCaseSupport.output(lot, today, policy);
    }

    private static <T> T agree(String field, T typed, T scanned) throws DomainException {
        if (typed != null && scanned != null && !Objects.equals(typed, scanned)) {
            throw new DomainException(field + " does not match the GS1 code (" + typed + " vs " + scanned + ").");
        }
        return typed != null ? typed : scanned;
    }
}

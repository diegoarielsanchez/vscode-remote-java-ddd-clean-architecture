package com.das.cleanddd.domain.catalog.usecases.services;

import java.time.Clock;
import java.time.LocalDate;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.events.ProductLotRejectedEvent;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.LotStatus;
import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;
import com.das.cleanddd.domain.catalog.lot.StockLot;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockLotOutputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Quality's decision that a quarantined lot cannot be used, e.g. after a temperature excursion. A reason is
 * required. Final: the units leave the stock on hand and await destruction, and the lot can no longer be released,
 * received into or delivered. Units of it already delivered are not affected: use the lot's recipients to reach
 * them.
 */
public final class RejectLotUseCase implements UseCase<LotInputDTO, StockLotOutputDTO> {

    private final IProductRepository products;
    private final IStockLotRepository lots;
    private final IProductEventPublisher publisher;
    private final UnitOfWork unitOfWork;
    private final ShelfLifePolicy policy;
    private final Clock clock;

    public RejectLotUseCase(IProductRepository products, IStockLotRepository lots, IProductEventPublisher publisher,
                            UnitOfWork unitOfWork, ShelfLifePolicy policy, Clock clock) {
        this.products = products;
        this.lots = lots;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public StockLotOutputDTO execute(LotInputDTO input) throws DomainException {
        return unitOfWork.execute(() -> {
            try {
                return doExecute(input);
            } catch (IllegalArgumentException e) {
                throw new DomainException(e.getMessage());
            }
        });
    }

    private StockLotOutputDTO doExecute(LotInputDTO input) throws DomainException {
        if (input == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        ProductUseCaseSupport.requireLotReason(input.reason());
        Product product = ProductUseCaseSupport.requireProduct(products, input.productId());
        StockLot lot = ProductUseCaseSupport.requireLot(lots, product, input.batchNumber());
        if (lot.isRejected()) {
            return ProductUseCaseSupport.output(lot, LocalDate.now(clock), policy);
        }
        if (lot.status() != LotStatus.QUARANTINED) {
            throw new DomainException("Only a quarantined lot can be rejected; quarantine batch "
                    + lot.batchNumber().value() + " first.");
        }
        lot = lot.withStatus(LotStatus.REJECTED);
        lots.save(lot);
        publisher.publish(new ProductLotRejectedEvent(product.getId().value(), lot.batchNumber().value(),
                lot.quantity(), lots.onHand(product.getId()), input.reason()));
        return ProductUseCaseSupport.output(lot, LocalDate.now(clock), policy);
    }
}

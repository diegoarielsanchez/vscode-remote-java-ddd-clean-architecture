package com.das.cleanddd.domain.catalog.usecases.services;

import java.time.Clock;
import java.time.LocalDate;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.events.ProductLotQuarantineLiftedEvent;
import com.das.cleanddd.domain.catalog.events.ProductLotQuarantinedEvent;
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
 * Puts a lot in quarantine (recall, quality investigation) or lifts it. A reason is required, and it is recorded in
 * the event with who did it. A rejected lot can do neither. Units already allocated to orders are not affected: use the lot's recipients to reach
 * them.
 */
public final class ChangeLotQuarantineUseCase implements UseCase<LotInputDTO, StockLotOutputDTO> {

    private final IProductRepository products;
    private final IStockLotRepository lots;
    private final IProductEventPublisher publisher;
    private final UnitOfWork unitOfWork;
    private final ShelfLifePolicy policy;
    private final Clock clock;
    private final LotStatus target;

    public ChangeLotQuarantineUseCase(IProductRepository products, IStockLotRepository lots,
                                      IProductEventPublisher publisher, UnitOfWork unitOfWork, ShelfLifePolicy policy,
                                      Clock clock, LotStatus target) {
        this.products = products;
        this.lots = lots;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
        this.policy = policy;
        this.clock = clock;
        this.target = target;
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
        ProductUseCaseSupport.requireNotRejected(lot);
        if (lot.status() != target) {
            lot = lot.withStatus(target);
            lots.save(lot);
            String id = product.getId().value();
            String batch = lot.batchNumber().value();
            publisher.publish(target == LotStatus.QUARANTINED
                    ? new ProductLotQuarantinedEvent(id, batch, input.reason())
                    : new ProductLotQuarantineLiftedEvent(id, batch, input.reason()));
        }
        return ProductUseCaseSupport.output(lot, LocalDate.now(clock), policy);
    }
}

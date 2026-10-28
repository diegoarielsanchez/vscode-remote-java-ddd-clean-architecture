package com.das.cleanddd.domain.catalog.usecases.services;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.events.ProductLotExcursionRecordedEvent;
import com.das.cleanddd.domain.catalog.events.ProductLotQuarantinedEvent;
import com.das.cleanddd.domain.catalog.lot.IStockLotRepository;
import com.das.cleanddd.domain.catalog.lot.LotStatus;
import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;
import com.das.cleanddd.domain.catalog.lot.StockLot;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.usecases.dtos.ExcursionInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ExcursionOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StorageConditionDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.shared.storage.ConditionReadings;
import com.das.cleanddd.domain.shared.storage.StorageCondition;

/**
 * Records what a data logger or monitoring system read for a lot and compares it with the product's storage
 * conditions (GDP: excursions are recorded, investigated, and the stock is not used until Quality decides).
 *
 * <ul>
 *   <li>Every record is kept, within the conditions or not, with who sent it.</li>
 *   <li>A breach puts an available lot in quarantine, giving the breaches as the reason. Quality then lifts the
 *       quarantine or rejects the lot.</li>
 *   <li>The product must have storage conditions on file; otherwise there is nothing to compare with.</li>
 * </ul>
 */
public final class RecordExcursionUseCase implements UseCase<ExcursionInputDTO, ExcursionOutputDTO> {

    static final int MAX_SOURCE = 100;
    static final int MAX_NOTE = 500;

    private final IProductRepository products;
    private final IStockLotRepository lots;
    private final IProductEventPublisher publisher;
    private final UnitOfWork unitOfWork;
    private final ShelfLifePolicy policy;
    private final Clock clock;

    public RecordExcursionUseCase(IProductRepository products, IStockLotRepository lots, IProductEventPublisher publisher,
                                  UnitOfWork unitOfWork, ShelfLifePolicy policy, Clock clock) {
        this.products = products;
        this.lots = lots;
        this.publisher = publisher;
        this.unitOfWork = unitOfWork;
        this.policy = policy;
        this.clock = clock;
    }

    @Override
    public ExcursionOutputDTO execute(ExcursionInputDTO input) throws DomainException {
        return unitOfWork.execute(() -> {
            try {
                return doExecute(input);
            } catch (IllegalArgumentException e) {
                throw new DomainException(e.getMessage());
            }
        });
    }

    private ExcursionOutputDTO doExecute(ExcursionInputDTO input) throws DomainException {
        if (input == null) {
            throw new DomainException("Input DTO cannot be null");
        }
        String source = input.source() == null ? "" : input.source().strip();
        if (source.isEmpty() || source.length() > MAX_SOURCE) {
            throw new DomainException("The source (logger, fridge or shipment) is required, at most " + MAX_SOURCE
                    + " characters.");
        }
        if (input.note() != null && input.note().length() > MAX_NOTE) {
            throw new DomainException("The note must be at most " + MAX_NOTE + " characters.");
        }
        if (input.observedFrom() == null || input.observedTo() == null) {
            throw new DomainException("The period observed (observedFrom and observedTo) is required.");
        }
        if (input.observedFrom().isAfter(input.observedTo())) {
            throw new DomainException("observedFrom cannot be after observedTo.");
        }
        if (input.observedTo().isAfter(clock.instant())) {
            throw new DomainException("Readings cannot be from the future.");
        }
        ConditionReadings readings = new ConditionReadings(input.minTemperatureC(), input.maxTemperatureC(),
                input.maxHumidityPercent(), input.lightExposed());

        Product product = ProductUseCaseSupport.requireProduct(products, input.productId());
        StorageCondition condition = product.getStorageCondition();
        if (condition == null) {
            throw new DomainException("This product has no storage conditions on file to compare the readings with;"
                    + " add them to the product first.");
        }
        StockLot lot = ProductUseCaseSupport.requireLot(lots, product, input.batchNumber());

        List<String> breaches = condition.breachesOf(readings);
        boolean quarantine = !breaches.isEmpty() && lot.status() == LotStatus.AVAILABLE;
        String id = product.getId().value();
        String batch = lot.batchNumber().value();
        publisher.publish(new ProductLotExcursionRecordedEvent(id, batch, source, input.observedFrom(),
                input.observedTo(), readings, breaches, quarantine, input.note()));
        if (quarantine) {
            lot = lot.withStatus(LotStatus.QUARANTINED);
            lots.save(lot);
            publisher.publish(new ProductLotQuarantinedEvent(id, batch, reason(source, readings, condition, breaches)));
        }
        return new ExcursionOutputDTO(ProductUseCaseSupport.output(lot, LocalDate.now(clock), policy),
                StorageConditionDTO.from(condition), breaches.isEmpty(), breaches, quarantine);
    }

    /** Fits the 500-character reason the quarantine events carry. */
    private static String reason(String source, ConditionReadings readings, StorageCondition condition,
                                 List<String> breaches) {
        String reason = "Excursion reported by " + source + ": " + readings.describe() + " against "
                + condition.describe() + ". " + String.join(" ", breaches);
        return reason.length() <= MAX_NOTE ? reason : reason.substring(0, MAX_NOTE - 1) + "…";
    }
}

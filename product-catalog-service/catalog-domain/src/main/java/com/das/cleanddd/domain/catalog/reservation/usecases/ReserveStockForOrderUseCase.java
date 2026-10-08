package com.das.cleanddd.domain.catalog.reservation.usecases;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.das.cleanddd.domain.catalog.entities.IProductRepository;
import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.events.ProductStockReservedEvent;
import com.das.cleanddd.domain.catalog.ports.IProductEventPublisher;
import com.das.cleanddd.domain.catalog.reservation.entities.IStockReservationRepository;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationLine;
import com.das.cleanddd.domain.catalog.reservation.entities.StockReservation;
import com.das.cleanddd.domain.catalog.reservation.ports.IStockReservationEventPublisher;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.UseCase;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/**
 * Saga step: reserve every line of a new order, all or nothing, in one {@link UnitOfWork}.
 *
 * <p>Each line is taken with the repository's atomic conditional decrement. If a line cannot be
 * served (unknown or inactive product, not enough stock), the lines already taken are given back in
 * the same transaction and a REJECTED reservation is stored — a business outcome, not an error, so
 * order-service hears about it. Either way the answer ({@code catalog.reservation.confirmed} or
 * {@code .rejected}) commits with the stock change through the outbox.
 *
 * <p>Idempotent: a second request for the same order returns the stored outcome and changes nothing.
 */
public final class ReserveStockForOrderUseCase implements UseCase<ReserveStockForOrderInputDTO, StockReservationOutputDTO> {

    private final IProductRepository products;
    private final IStockReservationRepository reservations;
    private final IProductEventPublisher productEvents;
    private final IStockReservationEventPublisher reservationEvents;
    private final UnitOfWork unitOfWork;
    private final Clock clock;

    public ReserveStockForOrderUseCase(IProductRepository products, IStockReservationRepository reservations,
                                       IProductEventPublisher productEvents,
                                       IStockReservationEventPublisher reservationEvents,
                                       UnitOfWork unitOfWork, Clock clock) {
        this.products = products;
        this.reservations = reservations;
        this.productEvents = productEvents;
        this.reservationEvents = reservationEvents;
        this.unitOfWork = unitOfWork;
        this.clock = clock;
    }

    @Override
    public StockReservationOutputDTO execute(ReserveStockForOrderInputDTO input) throws DomainException {
        if (input == null || input.orderId() == null || input.orderId().isBlank()) {
            throw new DomainException("Order Id is required.");
        }
        if (input.lines().isEmpty()) {
            throw new DomainException("An order must have at least one line.");
        }
        return unitOfWork.execute(() -> {
            Optional<StockReservation> existing = reservations.findByOrderId(input.orderId());
            if (existing.isPresent()) {
                return output(existing.get());
            }
            StockReservation reservation = reserveAll(input);
            reservations.save(reservation);
            reservation.pullDomainEvents().forEach(reservationEvents::publish);
            return output(reservation);
        });
    }

    private StockReservation reserveAll(ReserveStockForOrderInputDTO input) {
        List<ReservationLine> reserved = new ArrayList<>();
        List<ProductStockReservedEvent> stockEvents = new ArrayList<>();
        for (ReserveStockForOrderInputDTO.Line line : input.lines()) {
            String shortfall = tryReserve(line, reserved, stockEvents);
            if (shortfall != null) {
                // Give back what this order already took — same transaction, so nothing leaks.
                reserved.forEach(r -> products.releaseStock(new ProductId(r.productId()), r.quantity()));
                List<ReservationLine> requested = input.lines().stream()
                        .map(l -> ReservationLine.requested(l.productId(), l.quantity())).toList();
                return StockReservation.rejected(input.orderId(), requested, shortfall, clock.instant());
            }
        }
        stockEvents.forEach(productEvents::publish);
        return StockReservation.reserved(input.orderId(), reserved, clock.instant());
    }

    /** @return {@code null} when the line is reserved, otherwise why it is not */
    private String tryReserve(ReserveStockForOrderInputDTO.Line line, List<ReservationLine> reserved,
                              List<ProductStockReservedEvent> stockEvents) {
        if (line.quantity() <= 0) {
            return "Invalid quantity for product " + line.productId();
        }
        ProductId id;
        try {
            id = new ProductId(line.productId());
        } catch (IllegalArgumentException e) {
            return "Unknown product " + line.productId();
        }
        Optional<Product> product = products.findById(id);
        if (product.isEmpty()) {
            return "Unknown product " + line.productId();
        }
        if (!Boolean.TRUE.equals(product.get().isActive())) {
            return "Product " + line.productId() + " is not available";
        }
        if (!products.tryReserveStock(id, line.quantity())) {
            return "Insufficient stock for product " + line.productId();
        }
        Product after = products.findById(id).orElse(product.get());
        reserved.add(new ReservationLine(line.productId(), line.quantity(),
                after.getName().value(), after.getPrice().value()));
        stockEvents.add(new ProductStockReservedEvent(line.productId(), line.quantity(), after.getStock().value()));
        return null;
    }

    static StockReservationOutputDTO output(StockReservation reservation) {
        return new StockReservationOutputDTO(reservation.orderId(), reservation.status().name(), reservation.reason());
    }
}

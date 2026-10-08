package com.das.cleanddd.domain.catalog.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.events.ProductDomainEvent;
import com.das.cleanddd.domain.catalog.events.ProductStockReleasedEvent;
import com.das.cleanddd.domain.catalog.events.ProductStockReservedEvent;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationLine;
import com.das.cleanddd.domain.catalog.reservation.entities.ReservationStatus;
import com.das.cleanddd.domain.catalog.reservation.entities.StockReservation;
import com.das.cleanddd.domain.catalog.reservation.events.StockReservationConfirmedEvent;
import com.das.cleanddd.domain.catalog.reservation.events.StockReservationDomainEvent;
import com.das.cleanddd.domain.catalog.reservation.events.StockReservationRejectedEvent;
import com.das.cleanddd.domain.catalog.reservation.events.StockReservationReleasedEvent;
import com.das.cleanddd.domain.catalog.reservation.usecases.FulfilStockReservationUseCase;
import com.das.cleanddd.domain.catalog.reservation.usecases.ReleaseStockForOrderUseCase;
import com.das.cleanddd.domain.catalog.reservation.usecases.ReserveStockForOrderInputDTO;
import com.das.cleanddd.domain.catalog.reservation.usecases.ReserveStockForOrderInputDTO.Line;
import com.das.cleanddd.domain.catalog.reservation.usecases.ReserveStockForOrderUseCase;
import com.das.cleanddd.domain.catalog.reservation.usecases.StockReservationOutputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

@DisplayName("Stock reservation saga (catalog side)")
class StockReservationSagaTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC);
    private final List<ProductDomainEvent> productEvents = new ArrayList<>();
    private final List<StockReservationDomainEvent> reservationEvents = new ArrayList<>();

    private InMemoryCatalog catalog;
    private ReserveStockForOrderUseCase reserve;
    private ReleaseStockForOrderUseCase release;
    private FulfilStockReservationUseCase fulfil;
    private ProductId amoxicillin;
    private ProductId ibuprofen;
    private final String order = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() throws Exception {
        catalog = new InMemoryCatalog();
        amoxicillin = catalog.add("Amoxicillin 500mg", "12.50", 10, true);
        ibuprofen = catalog.add("Ibuprofen 400mg", "3.00", 2, true);
        reserve = new ReserveStockForOrderUseCase(catalog, catalog, productEvents::add, reservationEvents::add,
                UnitOfWork.immediate(), clock);
        release = new ReleaseStockForOrderUseCase(catalog, catalog, productEvents::add, reservationEvents::add,
                UnitOfWork.immediate(), clock);
        fulfil = new FulfilStockReservationUseCase(catalog, UnitOfWork.immediate(), clock);
    }

    private StockReservationOutputDTO reserve(Line... lines) throws DomainException {
        return reserve.execute(new ReserveStockForOrderInputDTO(order, List.of(lines)));
    }

    @Test
    @DisplayName("reserves every line, snapshots name and price, and confirms")
    void reservesEveryLine() throws DomainException {
        StockReservationOutputDTO out = reserve(new Line(amoxicillin.value(), 4), new Line(ibuprofen.value(), 2));

        assertEquals("RESERVED", out.status());
        assertEquals(6, catalog.stock(amoxicillin));
        assertEquals(0, catalog.stock(ibuprofen));
        StockReservationConfirmedEvent confirmed = assertInstanceOf(StockReservationConfirmedEvent.class, reservationEvents.get(0));
        assertEquals(order, confirmed.orderId());
        assertEquals(new ReservationLine(amoxicillin.value(), 4, "Amoxicillin 500mg", new BigDecimal("12.50")),
                confirmed.lines().get(0));
        assertEquals(2, productEvents.stream().filter(ProductStockReservedEvent.class::isInstance).count());
    }

    @Test
    @DisplayName("all or nothing: a short line rejects the order and gives back what was already taken")
    void rejectsAndGivesBack() throws DomainException {
        StockReservationOutputDTO out = reserve(new Line(amoxicillin.value(), 4), new Line(ibuprofen.value(), 3));

        assertEquals("REJECTED", out.status());
        assertEquals("Insufficient stock for product " + ibuprofen.value(), out.reason());
        assertEquals(10, catalog.stock(amoxicillin), "the first line's stock is back");
        assertEquals(2, catalog.stock(ibuprofen));
        assertInstanceOf(StockReservationRejectedEvent.class, reservationEvents.get(0));
        assertTrue(productEvents.isEmpty(), "no stock moved, so no stock events");
    }

    @Test
    @DisplayName("unknown and inactive products are rejected")
    void rejectsUnknownAndInactiveProducts() throws Exception {
        ProductId inactive = catalog.add("Discontinued", "1.00", 100, false);

        assertEquals("Unknown product " + UUID.fromString("00000000-0000-4000-8000-000000000000"),
                reserve.execute(new ReserveStockForOrderInputDTO(UUID.randomUUID().toString(),
                        List.of(new Line("00000000-0000-4000-8000-000000000000", 1)))).reason());
        assertTrue(reserve.execute(new ReserveStockForOrderInputDTO(UUID.randomUUID().toString(),
                List.of(new Line(inactive.value(), 1)))).reason().contains("not available"));
        assertEquals(100, catalog.stock(inactive));
    }

    @Test
    @DisplayName("a redelivered order.created changes nothing")
    void reservingTwiceIsIdempotent() throws DomainException {
        reserve(new Line(amoxicillin.value(), 4));
        reservationEvents.clear();
        productEvents.clear();

        StockReservationOutputDTO again = reserve(new Line(amoxicillin.value(), 4));

        assertEquals("RESERVED", again.status());
        assertEquals(6, catalog.stock(amoxicillin));
        assertTrue(reservationEvents.isEmpty());
        assertTrue(productEvents.isEmpty());
    }

    @Test
    @DisplayName("release gives the stock back once and announces it")
    void releaseGivesStockBackOnce() throws DomainException {
        reserve(new Line(amoxicillin.value(), 4), new Line(ibuprofen.value(), 1));
        reservationEvents.clear();
        productEvents.clear();

        assertEquals("RELEASED", release.execute(order).status());
        assertEquals("RELEASED", release.execute(order).status());

        assertEquals(10, catalog.stock(amoxicillin));
        assertEquals(2, catalog.stock(ibuprofen));
        assertEquals(1, reservationEvents.size());
        assertInstanceOf(StockReservationReleasedEvent.class, reservationEvents.get(0));
        assertEquals(2, productEvents.stream().filter(ProductStockReleasedEvent.class::isInstance).count());
    }

    @Test
    @DisplayName("release of a rejected or unknown reservation is a no-op")
    void releaseOfNothingIsANoOp() throws DomainException {
        reserve(new Line(ibuprofen.value(), 3)); // rejected
        reservationEvents.clear();

        assertEquals("REJECTED", release.execute(order).status());
        assertNull(release.execute(UUID.randomUUID().toString()).status());
        assertEquals(2, catalog.stock(ibuprofen));
        assertTrue(reservationEvents.isEmpty());
    }

    @Test
    @DisplayName("a fulfilled reservation can no longer be released")
    void fulfilledCannotBeReleased() throws DomainException {
        reserve(new Line(amoxicillin.value(), 4));

        assertEquals("FULFILLED", fulfil.execute(order).status());
        assertEquals("FULFILLED", release.execute(order).status());
        assertEquals(6, catalog.stock(amoxicillin));
        assertEquals(ReservationStatus.FULFILLED, catalog.reservations.get(order).status());
    }

    @Test
    @DisplayName("an order without lines or id is refused")
    void refusesInvalidInput() {
        assertThrows(DomainException.class, () -> reserve.execute(new ReserveStockForOrderInputDTO(order, List.of())));
        assertThrows(DomainException.class, () -> reserve.execute(new ReserveStockForOrderInputDTO(null,
                List.of(new Line(amoxicillin.value(), 1)))));
    }

    @Test
    @DisplayName("aggregate: release and fulfil only move a RESERVED reservation")
    void aggregateTransitions() {
        StockReservation rejected = StockReservation.rejected(order, List.of(ReservationLine.requested(amoxicillin.value(), 1)),
                "no", clock.instant());
        rejected.pullDomainEvents();
        assertEquals(rejected, rejected.release(clock.instant()));
        assertTrue(rejected.release(clock.instant()).pullDomainEvents().isEmpty());
        assertEquals(ReservationStatus.REJECTED, rejected.fulfil(clock.instant()).status());
    }
}

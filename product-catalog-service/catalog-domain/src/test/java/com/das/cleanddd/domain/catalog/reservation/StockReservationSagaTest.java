package com.das.cleanddd.domain.catalog.reservation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.catalog.InMemoryCatalog;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;
import com.das.cleanddd.domain.catalog.lot.StockAllocator;
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
        useShelfLife(ShelfLifePolicy.NONE);
        fulfil = new FulfilStockReservationUseCase(catalog, UnitOfWork.immediate(), clock);
    }

    private void useShelfLife(ShelfLifePolicy policy) {
        StockAllocator allocator = new StockAllocator(catalog, policy, clock);
        reserve = new ReserveStockForOrderUseCase(catalog, catalog, allocator, catalog, productEvents::add,
                reservationEvents::add, UnitOfWork.immediate(), clock);
        release = new ReleaseStockForOrderUseCase(catalog, allocator, catalog, productEvents::add, reservationEvents::add,
                UnitOfWork.immediate(), clock);
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
        ReservationLine line = confirmed.lines().get(0);
        assertEquals(new ReservationLine(amoxicillin.value(), 4, "Amoxicillin 500mg", new BigDecimal("12.50"),
                line.allocations()), line);
        assertEquals(4, line.allocations().get(0).quantity(), "served from the product's lot");
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
    @DisplayName("FEFO: the earliest expiring lot goes first and a line may span lots; the lots are kept on the line")
    void takesFirstExpiryFirst() throws Exception {
        ProductId insulin = catalog.addProduct("Insulin 100IU", "20.00", true, null);
        catalog.addLot(insulin, "LATE", LocalDate.of(2027, 6, 30), 10);
        catalog.addLot(insulin, "EARLY", LocalDate.of(2026, 12, 31), 3);
        catalog.addLot(insulin, "MIDDLE", LocalDate.of(2027, 1, 31), 5);

        assertEquals("RESERVED", reserve(new Line(insulin.value(), 6)).status());

        assertEquals(List.of(List.of("EARLYx3", "MIDDLEx3")), catalog.allocationsOf(order));
        assertEquals(0, catalog.lotQuantity(insulin, "EARLY"));
        assertEquals(2, catalog.lotQuantity(insulin, "MIDDLE"));
        assertEquals(10, catalog.lotQuantity(insulin, "LATE"));
    }

    @Test
    @DisplayName("expired and quarantined lots are skipped; an expiring lot is usable on its last day")
    void skipsExpiredAndQuarantinedLots() throws Exception {
        ProductId insulin = catalog.addProduct("Insulin 100IU", "20.00", true, null);
        catalog.addLot(insulin, "EXPIRED", LocalDate.of(2026, 10, 7), 50);
        catalog.addLot(insulin, "RECALLED", LocalDate.of(2026, 11, 30), 50);
        catalog.addLot(insulin, "LASTDAY", LocalDate.of(2026, 10, 8), 2);
        catalog.quarantine(insulin, "RECALLED");

        assertEquals("RESERVED", reserve(new Line(insulin.value(), 2)).status());
        assertEquals(List.of(List.of("LASTDAYx2")), catalog.allocationsOf(order));

        StockReservationOutputDTO more = reserve.execute(new ReserveStockForOrderInputDTO(UUID.randomUUID().toString(),
                List.of(new Line(insulin.value(), 1))));
        assertEquals("REJECTED", more.status(), "100 units on hand, none deliverable");
        assertEquals("Insufficient stock for product " + insulin.value(), more.reason());
        assertEquals(100, catalog.stock(insulin));
    }

    @Test
    @DisplayName("a lot short of the minimum remaining shelf life is not delivered")
    void respectsMinimumRemainingShelfLife() throws Exception {
        useShelfLife(new ShelfLifePolicy(30));
        ProductId insulin = catalog.addProduct("Insulin 100IU", "20.00", true, null);
        catalog.addLot(insulin, "SHORT", LocalDate.of(2026, 11, 6), 5);  // 29 days left on 2026-10-08
        catalog.addLot(insulin, "ENOUGH", LocalDate.of(2026, 11, 7), 5); // 30 days left

        assertEquals("RESERVED", reserve(new Line(insulin.value(), 5)).status());
        assertEquals(List.of(List.of("ENOUGHx5")), catalog.allocationsOf(order));
        assertEquals("REJECTED", reserve.execute(new ReserveStockForOrderInputDTO(UUID.randomUUID().toString(),
                List.of(new Line(insulin.value(), 1)))).status());
    }

    @Test
    @DisplayName("a rejected order gives back to the very lots it took from, and a release does too")
    void givesBackToTheSameLots() throws Exception {
        ProductId insulin = catalog.addProduct("Insulin 100IU", "20.00", true, null);
        catalog.addLot(insulin, "EARLY", LocalDate.of(2026, 12, 31), 3);
        catalog.addLot(insulin, "LATE", LocalDate.of(2027, 6, 30), 10);

        StockReservationOutputDTO rejected = reserve.execute(new ReserveStockForOrderInputDTO(UUID.randomUUID().toString(),
                List.of(new Line(insulin.value(), 5), new Line(ibuprofen.value(), 3))));
        assertEquals("REJECTED", rejected.status());
        assertEquals(3, catalog.lotQuantity(insulin, "EARLY"));
        assertEquals(10, catalog.lotQuantity(insulin, "LATE"));

        reserve(new Line(insulin.value(), 5));
        release.execute(order);
        assertEquals(3, catalog.lotQuantity(insulin, "EARLY"));
        assertEquals(10, catalog.lotQuantity(insulin, "LATE"));
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

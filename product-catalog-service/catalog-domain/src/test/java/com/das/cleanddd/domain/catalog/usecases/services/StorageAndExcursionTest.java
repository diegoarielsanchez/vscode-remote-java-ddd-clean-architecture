package com.das.cleanddd.domain.catalog.usecases.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.catalog.InMemoryCatalog;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.events.ProductDomainEvent;
import com.das.cleanddd.domain.catalog.events.ProductLotExcursionRecordedEvent;
import com.das.cleanddd.domain.catalog.events.ProductLotQuarantinedEvent;
import com.das.cleanddd.domain.catalog.events.ProductLotRejectedEvent;
import com.das.cleanddd.domain.catalog.events.ProductUpdatedEvent;
import com.das.cleanddd.domain.catalog.lot.LotStatus;
import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;
import com.das.cleanddd.domain.catalog.lot.StockAllocator;
import com.das.cleanddd.domain.catalog.reservation.entities.StockReservation;
import com.das.cleanddd.domain.catalog.reservation.usecases.ReserveStockForOrderInputDTO;
import com.das.cleanddd.domain.catalog.reservation.usecases.ReserveStockForOrderUseCase;
import com.das.cleanddd.domain.catalog.usecases.dtos.ExcursionInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ExcursionOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReceiveStockLotInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StorageConditionDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.UpdateProductInputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.audit.FieldChange;
import com.das.cleanddd.domain.shared.exceptions.DomainException;
import com.das.cleanddd.domain.shared.storage.StorageCondition;

/** GDP storage conditions: on the product, compared with logger readings, and carried on reservations. */
class StorageAndExcursionTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
    private static final StorageCondition FRIDGE =
            new StorageCondition(new BigDecimal("2"), new BigDecimal("8"), null, true);
    private static final String BATCH = "L2026-07A";

    private final List<ProductDomainEvent> events = new ArrayList<>();
    private InMemoryCatalog catalog;
    private ProductId insulin;
    private RecordExcursionUseCase record;
    private RejectLotUseCase reject;
    private ChangeLotQuarantineUseCase lift;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new InMemoryCatalog();
        insulin = catalog.addProduct("Insulin 100IU", "20.00", true, null);
        catalog.setStorageCondition(insulin, FRIDGE);
        catalog.addLot(insulin, BATCH, InMemoryCatalog.FAR_FUTURE, 40);
        UnitOfWork uow = UnitOfWork.immediate();
        record = new RecordExcursionUseCase(catalog, catalog, events::add, uow, ShelfLifePolicy.NONE, NOW);
        reject = new RejectLotUseCase(catalog, catalog, events::add, uow, ShelfLifePolicy.NONE, NOW);
        lift = new ChangeLotQuarantineUseCase(catalog, catalog, events::add, uow, ShelfLifePolicy.NONE, NOW,
                LotStatus.AVAILABLE);
    }

    private ExcursionOutputDTO readings(String min, String max, Boolean light) throws DomainException {
        return record.execute(new ExcursionInputDTO(insulin.value(), BATCH, "Fridge 3 logger",
                Instant.parse("2026-10-23T22:00:00Z"), Instant.parse("2026-10-24T06:00:00Z"),
                min == null ? null : new BigDecimal(min), max == null ? null : new BigDecimal(max), null, light, null));
    }

    @Nested
    @DisplayName("Excursions")
    class Excursions {

        @Test
        @DisplayName("readings within the conditions are recorded and change nothing")
        void withinConditions() throws DomainException {
            ExcursionOutputDTO out = readings("3.2", "7.8", false);

            assertTrue(out.withinConditions());
            assertFalse(out.quarantined());
            assertEquals("AVAILABLE", out.lot().status());
            ProductLotExcursionRecordedEvent event = assertInstanceOf(ProductLotExcursionRecordedEvent.class,
                    events.get(0));
            assertEquals("Fridge 3 logger", event.source());
            assertEquals(1, events.size());
        }

        @Test
        @DisplayName("a breach quarantines the lot, giving the breaches as the reason")
        void breachQuarantines() throws DomainException {
            ExcursionOutputDTO out = readings("3.0", "12.4", null);

            assertFalse(out.withinConditions());
            assertTrue(out.quarantined());
            assertEquals(List.of("Highest temperature 12.4 °C is above 8.0 °C."), out.breaches());
            assertEquals("QUARANTINED", out.lot().status());
            assertFalse(out.lot().allocatable());
            ProductLotQuarantinedEvent quarantined = assertInstanceOf(ProductLotQuarantinedEvent.class, events.get(1));
            assertEquals("Excursion reported by Fridge 3 logger: 3.0 to 12.4 °C against 2.0 to 8.0 °C, protect from"
                    + " light. Highest temperature 12.4 °C is above 8.0 °C.", quarantined.reason());
        }

        @Test
        @DisplayName("a second breach on a quarantined lot is recorded without another quarantine")
        void alreadyQuarantined() throws DomainException {
            readings(null, "12.4", null);
            ExcursionOutputDTO again = readings(null, null, true);

            assertFalse(again.withinConditions());
            assertFalse(again.quarantined());
            assertEquals(3, events.size(), "excursion, quarantine, excursion");
        }

        @Test
        @DisplayName("a product without storage conditions has nothing to compare readings with")
        void needsConditions() throws Exception {
            ProductId plain = catalog.add("Plain", "1.00", 5, true);
            DomainException e = assertThrows(DomainException.class, () -> record.execute(new ExcursionInputDTO(
                    plain.value(), "L-" + plain.value().substring(0, 8), "Logger", Instant.parse("2026-10-23T00:00:00Z"),
                    Instant.parse("2026-10-24T00:00:00Z"), null, new BigDecimal("30"), null, null, null)));
            assertTrue(e.getMessage().startsWith("This product has no storage conditions on file"), e.getMessage());
        }

        @Test
        @DisplayName("the source, the period and at least one reading are required; readings cannot be from the future")
        void validation() {
            assertThrows(DomainException.class, () -> record.execute(new ExcursionInputDTO(insulin.value(), BATCH, " ",
                    Instant.parse("2026-10-23T00:00:00Z"), Instant.parse("2026-10-24T00:00:00Z"), null,
                    new BigDecimal("9"), null, null, null)));
            assertThrows(DomainException.class, () -> record.execute(new ExcursionInputDTO(insulin.value(), BATCH,
                    "Logger", Instant.parse("2026-10-24T00:00:00Z"), Instant.parse("2026-10-23T00:00:00Z"), null,
                    new BigDecimal("9"), null, null, null)));
            assertThrows(DomainException.class, () -> record.execute(new ExcursionInputDTO(insulin.value(), BATCH,
                    "Logger", Instant.parse("2026-10-24T00:00:00Z"), Instant.parse("2026-10-25T00:00:00Z"), null,
                    new BigDecimal("9"), null, null, null)));
            assertThrows(DomainException.class, () -> readings(null, null, null));
            assertTrue(events.isEmpty());
        }
    }

    @Nested
    @DisplayName("Quality decision")
    class QualityDecision {

        @Test
        @DisplayName("only a quarantined lot can be rejected, with a reason")
        void rejectNeedsQuarantine() {
            DomainException e = assertThrows(DomainException.class,
                    () -> reject.execute(new LotInputDTO(insulin.value(), BATCH, "Stability data do not cover it")));
            assertTrue(e.getMessage().startsWith("Only a quarantined lot can be rejected"), e.getMessage());
            assertThrows(DomainException.class, () -> reject.execute(new LotInputDTO(insulin.value(), BATCH, " ")));
        }

        @Test
        @DisplayName("a rejected lot leaves the stock on hand for good")
        void rejectedIsFinal() throws DomainException {
            readings(null, "12.4", null);

            assertEquals("REJECTED", reject.execute(new LotInputDTO(insulin.value(), BATCH,
                    "Stability data do not cover 12.4 °C")).status());

            ProductLotRejectedEvent rejected = assertInstanceOf(ProductLotRejectedEvent.class, events.get(2));
            assertEquals(40, rejected.quantity());
            assertEquals(0, rejected.remainingStock());
            assertEquals(0, catalog.stock(insulin));
            assertThrows(DomainException.class,
                    () -> lift.execute(new LotInputDTO(insulin.value(), BATCH, "Released after all")));
            ReceiveStockLotUseCase receive = new ReceiveStockLotUseCase(catalog, catalog, events::add,
                    UnitOfWork.immediate(), ShelfLifePolicy.NONE, NOW);
            assertThrows(DomainException.class, () -> receive.execute(new ReceiveStockLotInputDTO(insulin.value(), 5,
                    BATCH, InMemoryCatalog.FAR_FUTURE, null)));
        }

        @Test
        @DisplayName("or Quality releases it by lifting the quarantine")
        void release() throws DomainException {
            readings(null, "8.6", null);

            assertEquals("AVAILABLE", lift.execute(new LotInputDTO(insulin.value(), BATCH,
                    "Within the stability budget for a short excursion")).status());
            assertEquals(40, catalog.stock(insulin));
        }
    }

    @Nested
    @DisplayName("On the product and the reservation")
    class ProductAndReservation {

        @Test
        @DisplayName("a change of storage conditions is in the audit trail; a medicine's cannot be removed")
        void changeIsAudited() throws DomainException {
            UpdateProductUseCase update = new UpdateProductUseCase(catalog, new ProductMapper(), events::add,
                    UnitOfWork.immediate());

            update.execute(new UpdateProductInputDTO(insulin.value(), "Insulin 100IU", "Test product",
                    new BigDecimal("20.00"), "BOX", null,
                    new StorageConditionDTO(new BigDecimal("2"), new BigDecimal("8"), 60, true), "New label"));

            ProductUpdatedEvent updated = assertInstanceOf(ProductUpdatedEvent.class, events.get(0));
            assertEquals(List.of(new FieldChange("storageCondition", "2.0 to 8.0 °C, protect from light",
                    "2.0 to 8.0 °C, at most 60 % RH, protect from light")), updated.changes());
            assertEquals("2.0 to 8.0 °C, at most 60 % RH, protect from light", updated.storageCondition().describe());
            assertThrows(DomainException.class, () -> update.execute(new UpdateProductInputDTO(insulin.value(),
                    "Insulin 100IU", null, new BigDecimal("20.00"), "BOX", null,
                    new StorageConditionDTO(new BigDecimal("8"), new BigDecimal("2"), null, false), "Typo")));
        }

        @Test
        @DisplayName("a reservation line carries the conditions the units must travel under")
        void reservationCarriesConditions() throws DomainException {
            ReserveStockForOrderUseCase reserve = new ReserveStockForOrderUseCase(catalog, catalog,
                    new StockAllocator(catalog, ShelfLifePolicy.NONE, NOW), catalog, events::add, e -> { },
                    UnitOfWork.immediate(), NOW);

            reserve.execute(new ReserveStockForOrderInputDTO("order-1",
                    List.of(new ReserveStockForOrderInputDTO.Line(insulin.value(), 4))));

            StockReservation reservation = catalog.reservations.get("order-1");
            assertEquals(FRIDGE, reservation.lines().get(0).storageCondition());
            assertTrue(reservation.lines().get(0).storageCondition().coldChain());
        }
    }
}

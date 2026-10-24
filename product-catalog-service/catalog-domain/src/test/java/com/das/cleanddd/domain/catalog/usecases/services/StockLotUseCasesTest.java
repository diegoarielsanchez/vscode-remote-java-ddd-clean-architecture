package com.das.cleanddd.domain.catalog.usecases.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.catalog.InMemoryCatalog;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.events.ProductDomainEvent;
import com.das.cleanddd.domain.catalog.events.ProductLotQuarantineLiftedEvent;
import com.das.cleanddd.domain.catalog.events.ProductLotQuarantinedEvent;
import com.das.cleanddd.domain.catalog.events.ProductRestockedEvent;
import com.das.cleanddd.domain.catalog.events.ProductUpdatedEvent;
import com.das.cleanddd.domain.shared.audit.FieldChange;
import com.das.cleanddd.domain.catalog.lot.Gs1ElementString;
import com.das.cleanddd.domain.catalog.lot.ShelfLifePolicy;
import com.das.cleanddd.domain.catalog.lot.StockAllocator;
import com.das.cleanddd.domain.catalog.reservation.usecases.ReleaseStockForOrderUseCase;
import com.das.cleanddd.domain.catalog.reservation.usecases.ReserveStockForOrderInputDTO;
import com.das.cleanddd.domain.catalog.reservation.usecases.ReserveStockForOrderUseCase;
import com.das.cleanddd.domain.catalog.usecases.dtos.CreateProductInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotQuantityInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.LotRecipientOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductIDDto;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductMapper;
import com.das.cleanddd.domain.catalog.usecases.dtos.ProductOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReceiveStockLotInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.ReserveStockOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockLotOutputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.StockQuantityInputDTO;
import com.das.cleanddd.domain.catalog.usecases.dtos.UpdateProductInputDTO;
import com.das.cleanddd.domain.shared.UnitOfWork;
import com.das.cleanddd.domain.shared.exceptions.DomainException;

/** "Today" is 2026-10-24. */
@DisplayName("Stock lots through the product use cases")
class StockLotUseCasesTest {

    private static final Clock TODAY = Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);
    private static final String GTIN = "09506000134352"; // GS1's example GTIN-14
    private static final LocalDate EXPIRES = LocalDate.of(2027, 3, 31);
    private static final char GS = Gs1ElementString.GS;

    private final List<ProductDomainEvent> events = new ArrayList<>();
    private final ProductMapper mapper = new ProductMapper();
    private InMemoryCatalog catalog;
    private ProductId insulin;

    private ReceiveStockLotUseCase receive;
    private ReserveProductStockUseCase reserve;
    private ReleaseProductStockUseCase release;
    private CheckProductAvailabilityUseCase availability;
    private ListStockLotsUseCase list;
    private ChangeLotQuarantineUseCase quarantine;
    private ChangeLotQuarantineUseCase lift;
    private FindLotRecipientsUseCase recipients;

    @BeforeEach
    void setUp() throws Exception {
        catalog = new InMemoryCatalog();
        insulin = catalog.addProduct("Insulin 100IU", "20.00", true, GTIN);
        UnitOfWork uow = UnitOfWork.immediate();
        StockAllocator allocator = new StockAllocator(catalog, ShelfLifePolicy.NONE, TODAY);
        receive = new ReceiveStockLotUseCase(catalog, catalog, events::add, uow, ShelfLifePolicy.NONE, TODAY);
        reserve = new ReserveProductStockUseCase(catalog, catalog, allocator, events::add, uow);
        release = new ReleaseProductStockUseCase(catalog, catalog, events::add, uow);
        availability = new CheckProductAvailabilityUseCase(catalog, allocator);
        list = new ListStockLotsUseCase(catalog, catalog, ShelfLifePolicy.NONE, TODAY);
        quarantine = new ChangeLotQuarantineUseCase(catalog, catalog, events::add, uow, ShelfLifePolicy.NONE, TODAY,
                com.das.cleanddd.domain.catalog.lot.LotStatus.QUARANTINED);
        lift = new ChangeLotQuarantineUseCase(catalog, catalog, events::add, uow, ShelfLifePolicy.NONE, TODAY,
                com.das.cleanddd.domain.catalog.lot.LotStatus.AVAILABLE);
        recipients = new FindLotRecipientsUseCase(catalog, catalog, catalog);
    }

    private StockLotOutputDTO receive(int quantity, String batch, LocalDate expiry, String gs1) throws DomainException {
        return receive.execute(new ReceiveStockLotInputDTO(insulin.value(), quantity, batch, expiry, gs1));
    }

    @Nested
    @DisplayName("Goods receipt")
    class GoodsReceipt {

        @Test
        @DisplayName("a typed batch and expiry become a lot; the event records them")
        void typed() throws DomainException {
            StockLotOutputDTO lot = receive(50, "L2026-07A", EXPIRES, null);

            assertEquals("L2026-07A", lot.batchNumber());
            assertEquals(EXPIRES, lot.expirationDate());
            assertEquals(50, lot.quantity());
            assertTrue(lot.allocatable());
            ProductRestockedEvent event = assertInstanceOf(ProductRestockedEvent.class, events.get(0));
            assertEquals("L2026-07A", event.batchNumber());
            assertEquals(EXPIRES, event.expirationDate());
            assertEquals(50, event.remainingStock());
        }

        @Test
        @DisplayName("the GS1 code, human-readable or raw from a scanner, supplies them; its serial is not stored")
        void fromGs1Code() throws DomainException {
            receive(5, null, null, "(01)" + GTIN + "(17)270331(10)L2026-07A(21)SN000123");
            StockLotOutputDTO lot = receive(5, null, null, "]d201" + GTIN + "17270331" + "10L2026-07A" + GS + "21SN000124");

            assertEquals("L2026-07A", lot.batchNumber());
            assertEquals(EXPIRES, lot.expirationDate());
            assertEquals(10, lot.quantity(), "the same batch twice adds up in one lot");
        }

        @Test
        @DisplayName("typed values must agree with the GS1 code")
        void typedMustAgree() {
            DomainException e = assertThrows(DomainException.class,
                    () -> receive(5, "L-OTHER", null, "(01)" + GTIN + "(17)270331(10)L2026-07A"));
            assertTrue(e.getMessage().startsWith("batchNumber does not match"), e.getMessage());
        }

        @Test
        @DisplayName("a code for another GTIN is refused, so the wrong item cannot be booked in")
        void otherGtinRefused() {
            DomainException e = assertThrows(DomainException.class,
                    () -> receive(5, null, null, "(01)04006381333931(17)270331(10)L1"));
            assertTrue(e.getMessage().contains("not this product's GTIN"), e.getMessage());
            assertEquals(0, catalog.stock(insulin));
        }

        @Test
        @DisplayName("batch and expiry are required; expired goods are refused")
        void requiredAndNotExpired() {
            assertThrows(DomainException.class, () -> StockLotUseCasesTest.this.receive(5, null, EXPIRES, null));
            assertThrows(DomainException.class, () -> receive(5, "L1", null, null));
            DomainException e = assertThrows(DomainException.class, () -> receive(5, "L1", LocalDate.of(2026, 10, 23), null));
            assertTrue(e.getMessage().startsWith("Expired goods cannot be received"), e.getMessage());
            assertEquals("L1", receiveOnLastDay().batchNumber(), "usable through its expiration day");
        }

        private StockLotOutputDTO receiveOnLastDay() {
            try {
                return receive(1, "L1", LocalDate.of(2026, 10, 24), null);
            } catch (DomainException e) {
                throw new AssertionError(e);
            }
        }

        @Test
        @DisplayName("one batch has one expiration date")
        void oneExpiryPerBatch() throws DomainException {
            receive(5, "L1", EXPIRES, null);
            DomainException e = assertThrows(DomainException.class, () -> receive(5, "L1", EXPIRES.plusDays(1), null));
            assertTrue(e.getMessage().contains("one batch has one expiration date"), e.getMessage());
            assertEquals(5, catalog.stock(insulin));
        }
    }

    @Nested
    @DisplayName("Reserving, releasing and availability")
    class Allocation {

        @Test
        @DisplayName("a manual reservation is first expiry first out and names its lots; release returns to a named lot")
        void manualReserveAndRelease() throws DomainException {
            receive(4, "LATE", EXPIRES, null);
            receive(4, "EARLY", EXPIRES.minusMonths(3), null);

            ReserveStockOutputDTO out = reserve.execute(new StockQuantityInputDTO(insulin.value(), 6));

            assertEquals(List.of("EARLY", "LATE"), out.lots().stream().map(a -> a.batchNumber()).toList());
            assertEquals(2, out.remainingStock());
            release.execute(new LotQuantityInputDTO(insulin.value(), "EARLY", 4));
            assertEquals(4, catalog.lotQuantity(insulin, "EARLY"));
            assertThrows(DomainException.class, () -> release.execute(new LotQuantityInputDTO(insulin.value(), "NOPE", 1)));
        }

        @Test
        @DisplayName("not enough deliverable stock takes nothing")
        void insufficient() throws DomainException {
            receive(4, "L1", EXPIRES, null);
            assertThrows(DomainException.class, () -> reserve.execute(new StockQuantityInputDTO(insulin.value(), 5)));
            assertEquals(4, catalog.lotQuantity(insulin, "L1"));
        }

        @Test
        @DisplayName("availability counts only deliverable lots")
        void availabilityCountsDeliverableLots() throws Exception {
            receive(4, "L1", EXPIRES, null);
            catalog.addLot(insulin, "OLD", LocalDate.of(2026, 10, 1), 100); // expired
            assertTrue(availability.execute(new StockQuantityInputDTO(insulin.value(), 4)).available());
            assertFalse(availability.execute(new StockQuantityInputDTO(insulin.value(), 5)).available());
        }
    }

    @Nested
    @DisplayName("Lots, quarantine and recall")
    class QuarantineAndRecall {

        @Test
        @DisplayName("lots are listed first expiry first out, with their state today")
        void listsInFefoOrder() throws Exception {
            receive(4, "LATE", EXPIRES, null);
            catalog.addLot(insulin, "OLD", LocalDate.of(2026, 10, 1), 2);
            receive(4, "EARLY", EXPIRES.minusMonths(3), null);

            List<StockLotOutputDTO> lots = list.execute(new ProductIDDto(insulin.value()));

            assertEquals(List.of("OLD", "EARLY", "LATE"), lots.stream().map(StockLotOutputDTO::batchNumber).toList());
            assertTrue(lots.get(0).expired());
            assertFalse(lots.get(0).allocatable());
        }

        @Test
        @DisplayName("a quarantined lot is not delivered until the quarantine is lifted; both need a reason")
        void quarantineBlocksDelivery() throws DomainException {
            receive(4, "L1", EXPIRES, null);
            assertThrows(DomainException.class, () -> quarantine.execute(new LotInputDTO(insulin.value(), "L1", " ")));

            StockLotOutputDTO blocked = quarantine.execute(new LotInputDTO(insulin.value(), "L1", "Recall R-2026-17"));
            assertEquals("QUARANTINED", blocked.status());
            assertFalse(blocked.allocatable());
            assertThrows(DomainException.class, () -> reserve.execute(new StockQuantityInputDTO(insulin.value(), 1)));
            assertEquals("Recall R-2026-17",
                    assertInstanceOf(ProductLotQuarantinedEvent.class, events.get(events.size() - 1)).reason());

            assertEquals("AVAILABLE", lift.execute(new LotInputDTO(insulin.value(), "L1", "Investigation closed")).status());
            assertInstanceOf(ProductLotQuarantineLiftedEvent.class, events.get(events.size() - 1));
            assertEquals(1, reserve.execute(new StockQuantityInputDTO(insulin.value(), 1)).lots().size());
        }

        @Test
        @DisplayName("a recall lists every order that received units of the lot")
        void recallListsRecipients() throws DomainException {
            receive(3, "EARLY", EXPIRES.minusMonths(3), null);
            receive(10, "LATE", EXPIRES, null);
            StockAllocator allocator = new StockAllocator(catalog, ShelfLifePolicy.NONE, TODAY);
            ReserveStockForOrderUseCase saga = new ReserveStockForOrderUseCase(catalog, catalog, allocator, catalog,
                    events::add, e -> { }, UnitOfWork.immediate(), TODAY);
            String first = UUID.randomUUID().toString();
            String second = UUID.randomUUID().toString();
            saga.execute(new ReserveStockForOrderInputDTO(first, List.of(new ReserveStockForOrderInputDTO.Line(insulin.value(), 5))));
            saga.execute(new ReserveStockForOrderInputDTO(second, List.of(new ReserveStockForOrderInputDTO.Line(insulin.value(), 2))));
            new ReleaseStockForOrderUseCase(catalog, allocator, catalog, events::add, e -> { }, UnitOfWork.immediate(), TODAY)
                    .execute(second);

            assertEquals(List.of(new LotRecipientOutputDTO(first, "RESERVED", 3)),
                    recipients.execute(new LotInputDTO(insulin.value(), "EARLY", null)));
            List<LotRecipientOutputDTO> late = recipients.execute(new LotInputDTO(insulin.value(), "LATE", null));
            assertEquals(2, late.size(), "released orders still show: they had the units for a while");
        }
    }

    @Nested
    @DisplayName("GTIN and the first lot")
    class GtinRules {

        private CreateProductUseCase create;
        private UpdateProductUseCase update;

        @BeforeEach
        void setUp() {
            create = new CreateProductUseCase(catalog, catalog, mapper, events::add, UnitOfWork.immediate(), TODAY);
            update = new UpdateProductUseCase(catalog, mapper, events::add, UnitOfWork.immediate());
        }

        @Test
        @DisplayName("creating with initial stock needs its batch and expiry, and makes the first lot")
        void initialStockIsAFirstLot() throws DomainException {
            assertThrows(DomainException.class, () -> create.execute(new CreateProductInputDTO("Aspirin", null,
                    new BigDecimal("1.00"), "BOX", 10)));

            ProductOutputDTO out = create.execute(new CreateProductInputDTO("Aspirin", null, new BigDecimal("1.00"), "BOX",
                    10, "4006381333931", "A-1", EXPIRES));

            assertEquals(10, out.stock());
            assertEquals("04006381333931", out.gtin(), "stored as GTIN-14");
            assertEquals(10, catalog.lotQuantity(new ProductId(out.id()), "A-1"));
        }

        @Test
        @DisplayName("a GTIN identifies one product only")
        void gtinIsUnique() {
            DomainException e = assertThrows(DomainException.class, () -> create.execute(new CreateProductInputDTO(
                    "Copy", null, new BigDecimal("1.00"), "BOX", 0, GTIN, null, null)));
            assertTrue(e.getMessage().contains("already identifies product"), e.getMessage());
        }

        @Test
        @DisplayName("a GTIN with a wrong check digit is refused")
        void checkDigit() {
            assertThrows(DomainException.class, () -> create.execute(new CreateProductInputDTO(
                    "Bad", null, new BigDecimal("1.00"), "BOX", 0, "09506000134353", null, null)));
        }

        @Test
        @DisplayName("an edit needs a reason, which the audit trail keeps")
        void anEditNeedsAReason() throws Exception {
            ProductId plain = catalog.addProduct("Plain", "1.00", true, null);

            DomainException e = assertThrows(DomainException.class, () -> update.execute(new UpdateProductInputDTO(
                    plain.value(), "Plain", null, new BigDecimal("2.00"), "BOX")));
            assertEquals("A reason for the change is required.", e.getMessage());

            update.execute(new UpdateProductInputDTO(plain.value(), "Plain", null, new BigDecimal("2.00"), "BOX", null,
                    "Price list 2027"));
            ProductUpdatedEvent updated = events.stream().filter(ProductUpdatedEvent.class::isInstance)
                    .map(ProductUpdatedEvent.class::cast).reduce((a, b) -> b).orElseThrow();
            assertEquals("Price list 2027", updated.reason());
            assertEquals(List.of(new FieldChange("description", "Test product", null),
                    new FieldChange("price", "1.00", "2.00")), updated.changes());
        }

        @Test
        @DisplayName("a GTIN can be set once, then neither changed nor removed")
        void gtinSetOnce() throws Exception {
            ProductId plain = catalog.addProduct("Plain", "1.00", true, null);
            assertEquals("04006381333931", update.execute(new UpdateProductInputDTO(plain.value(), "Plain", null,
                    new BigDecimal("1.00"), "BOX", "4006381333931", "GTIN assigned by GS1")).gtin());
            assertEquals("04006381333931", update.execute(new UpdateProductInputDTO(plain.value(), "Plain", null,
                    new BigDecimal("2.00"), "BOX", null, "Price list 2027")).gtin(), "left out: kept");
            assertThrows(DomainException.class, () -> update.execute(new UpdateProductInputDTO(plain.value(), "Plain",
                    null, new BigDecimal("1.00"), "BOX", "96385074", "Wrong GTIN")));
        }
    }

    @Nested
    @DisplayName("Promotional material (not lot-tracked)")
    class PromotionalMaterial {

        private CreateProductUseCase create;
        private ProductId brochure;

        @BeforeEach
        void setUp() throws DomainException {
            create = new CreateProductUseCase(catalog, catalog, mapper, events::add, UnitOfWork.immediate(), TODAY);
            ProductOutputDTO out = create.execute(new CreateProductInputDTO("Insulin patient brochure", null,
                    new BigDecimal("0.00"), "UNIT", 100, null, null, null, false));
            brochure = new ProductId(out.id());
            assertFalse(out.lotTracked());
            catalog.save(catalog.findById(brochure).orElseThrow().setActivate());
            events.clear();
        }

        private StockLotOutputDTO receive(int quantity, String batch, LocalDate expiry) throws DomainException {
            return receive.execute(new ReceiveStockLotInputDTO(brochure.value(), quantity, batch, expiry, null));
        }

        @Test
        @DisplayName("units without a batch or expiry go into the product's UNBATCHED lot")
        void unbatched() throws DomainException {
            StockLotOutputDTO lot = receive(50, null, null);

            assertEquals("UNBATCHED", lot.batchNumber());
            assertEquals(null, lot.expirationDate());
            assertEquals(150, lot.quantity(), "initial stock and the receipt share the lot");
            assertTrue(lot.allocatable());
            assertEquals(1, list.execute(new ProductIDDto(brochure.value())).size());
        }

        @Test
        @DisplayName("an approval code with the date its approval ends is a batch that is delivered first")
        void approvalCodeWithEndDate() throws DomainException {
            receive(10, "PP-INS-0042", LocalDate.of(2026, 12, 31));

            ReserveStockOutputDTO reserved = reserve.execute(new StockQuantityInputDTO(brochure.value(), 4));

            assertEquals(6, catalog.lotQuantity(brochure, "PP-INS-0042"), "the dated batch goes first");
            assertEquals(100, catalog.lotQuantity(brochure, "UNBATCHED"), "undated units last");
            assertEquals(1, reserved.lots().size());
        }

        @Test
        @DisplayName("material past its approval end date is refused")
        void expiredApprovalRefused() {
            DomainException e = assertThrows(DomainException.class,
                    () -> receive(10, "PP-INS-0041", LocalDate.of(2026, 10, 1)));
            assertTrue(e.getMessage().startsWith("Expired goods cannot be received"), e.getMessage());
        }

        @Test
        @DisplayName("an expiration date needs a batch number")
        void expiryNeedsBatch() {
            DomainException e = assertThrows(DomainException.class, () -> receive(10, null, EXPIRES));
            assertTrue(e.getMessage().startsWith("An expiration date needs a batch number"), e.getMessage());
        }

        @Test
        @DisplayName("release, quarantine and recipients work on the UNBATCHED lot without a batch number")
        void lotOperationsWithoutBatch() throws DomainException {
            reserve.execute(new StockQuantityInputDTO(brochure.value(), 30));
            release.execute(new LotQuantityInputDTO(brochure.value(), null, 10));
            assertEquals(80, catalog.lotQuantity(brochure, "UNBATCHED"));

            quarantine.execute(new LotInputDTO(brochure.value(), null, "Superseded by a new version"));
            assertFalse(availability.execute(new StockQuantityInputDTO(brochure.value(), 1)).available());
            assertTrue(recipients.execute(new LotInputDTO(brochure.value(), null, null)).isEmpty());
        }

        @Test
        @DisplayName("a lot-tracked product still needs a batch, and cannot use UNBATCHED")
        void medicinesKeepTheirRules() {
            assertThrows(DomainException.class, () -> StockLotUseCasesTest.this.receive(5, null, EXPIRES, null));
            DomainException e = assertThrows(DomainException.class, () -> StockLotUseCasesTest.this.receive(5, "UNBATCHED", EXPIRES, null));
            assertTrue(e.getMessage().contains("is reserved"), e.getMessage());
            assertThrows(DomainException.class,
                    () -> release.execute(new LotQuantityInputDTO(insulin.value(), null, 1)));
        }

        @Test
        @DisplayName("updating the product keeps it not lot-tracked")
        void updateKeepsTheFlag() throws DomainException {
            UpdateProductUseCase update = new UpdateProductUseCase(catalog, mapper, events::add, UnitOfWork.immediate());
            assertFalse(update.execute(new UpdateProductInputDTO(brochure.value(), "Brochure v2", null,
                    new BigDecimal("0.00"), "UNIT", null, "New edition")).lotTracked());
        }
    }
}

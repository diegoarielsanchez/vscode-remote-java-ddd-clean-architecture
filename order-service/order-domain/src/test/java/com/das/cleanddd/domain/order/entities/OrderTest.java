package com.das.cleanddd.domain.order.entities;

import java.math.BigDecimal;
import java.util.List;

import com.das.cleanddd.domain.order.events.OrderCreatedEvent;
import com.das.cleanddd.domain.order.events.OrderDomainEvent;
import com.das.cleanddd.domain.order.events.OrderRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderStockRejectedEvent;
import com.das.cleanddd.domain.order.events.OrderSubmittedForApprovalEvent;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Order Aggregate Root")
class OrderTest {

    private MedicalSalesRepId medicalSalesRepId;
    private ProductId productA;
    private ProductId productB;
    private List<OrderLine> lines;

    @BeforeEach
    void setUp() throws BusinessValidationException {
        medicalSalesRepId = new MedicalSalesRepId(java.util.UUID.randomUUID().toString());
        productA = new ProductId(java.util.UUID.randomUUID().toString());
        productB = new ProductId(java.util.UUID.randomUUID().toString());
        lines = List.of(
                OrderLine.unpriced(productA, new OrderLineQuantity(10)),
                OrderLine.unpriced(productB, new OrderLineQuantity(2)));
    }

    private List<ReservedLinePrice> prices() throws BusinessValidationException {
        return List.of(
                new ReservedLinePrice(productA, "Amoxicillin 500mg", new OrderLineUnitPrice(new BigDecimal("12.50"))),
                new ReservedLinePrice(productB, "Ibuprofen 400mg", new OrderLineUnitPrice(new BigDecimal("3.00"))));
    }

    private Order pending() throws BusinessValidationException {
        Order pending = Order.create(medicalSalesRepId, lines).confirmStock(prices());
        pending.pullDomainEvents(); // as after the save that persisted it
        return pending;
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("create() should start AWAITING_STOCK, unpriced, and record OrderCreatedEvent with the lines")
        void shouldCreateAwaitingStock() throws BusinessValidationException {
            Order order = Order.create(medicalSalesRepId, lines);

            assertEquals(OrderStatus.AWAITING_STOCK, order.status());
            assertNull(order.totalAmount(), "no total before the catalog prices the lines");
            assertTrue(order.lines().stream().noneMatch(OrderLine::isPriced));
            var events = order.pullDomainEvents();
            assertEquals(1, events.size());
            OrderCreatedEvent created = assertInstanceOf(OrderCreatedEvent.class, events.get(0));
            assertEquals(medicalSalesRepId.value(), created.medicalSalesRepId());
            assertEquals(List.of(new OrderCreatedEvent.Line(productA.value(), 10),
                    new OrderCreatedEvent.Line(productB.value(), 2)), created.lines());
        }

        @Test
        @DisplayName("should reject an empty line list")
        void shouldRejectEmptyLines() {
            assertThrows(BusinessValidationException.class,
                    () -> new Order(null, medicalSalesRepId, List.of(), OrderStatus.AWAITING_STOCK,
                            null, null, null, null, null, null, null));
        }

        @Test
        @DisplayName("should reject more than MAX_LINES lines")
        void shouldRejectTooManyLines() throws BusinessValidationException {
            List<OrderLine> many = new java.util.ArrayList<>();
            for (int i = 0; i <= Order.MAX_LINES; i++) {
                many.add(OrderLine.unpriced(new ProductId(java.util.UUID.randomUUID().toString()), new OrderLineQuantity(1)));
            }
            assertThrows(BusinessValidationException.class, () -> Order.create(medicalSalesRepId, many));
        }

        @Test
        @DisplayName("should reject a null medicalSalesRepId")
        void shouldRejectNullMsr() {
            assertThrows(BusinessValidationException.class,
                    () -> new Order(null, null, lines, OrderStatus.AWAITING_STOCK,
                            null, null, null, null, null, null, null));
        }
    }

    @Nested
    @DisplayName("Stock saga")
    class StockSaga {

        @Test
        @DisplayName("confirmStock() prices every line, moves to PENDING_APPROVAL and records the total")
        void confirmStockPricesLines() throws BusinessValidationException {
            Order created = Order.create(medicalSalesRepId, lines);
            created.pullDomainEvents();

            Order pending = created.confirmStock(prices());

            assertEquals(OrderStatus.PENDING_APPROVAL, pending.status());
            assertEquals(0, new BigDecimal("131.00").compareTo(pending.totalAmount()));
            assertEquals("Amoxicillin 500mg", pending.lines().get(0).productNameSnapshot());
            assertEquals(created.lines().get(0).id(), pending.lines().get(0).id(), "line identity survives pricing");
            var events = pending.pullDomainEvents();
            assertEquals(1, events.size());
            OrderSubmittedForApprovalEvent submitted = assertInstanceOf(OrderSubmittedForApprovalEvent.class, events.get(0));
            assertEquals(0, new BigDecimal("131.00").compareTo(submitted.totalAmount()));
        }

        @Test
        @DisplayName("confirmStock() refuses a reservation that misses a product")
        void confirmStockNeedsEveryProduct() throws BusinessValidationException {
            Order created = Order.create(medicalSalesRepId, lines);
            List<ReservedLinePrice> onlyA = List.of(prices().get(0));
            assertThrows(BusinessValidationException.class, () -> created.confirmStock(onlyA));
        }

        @Test
        @DisplayName("confirmStock() refuses an order that is not AWAITING_STOCK")
        void confirmStockOnlyFromAwaitingStock() throws BusinessValidationException {
            Order pending = pending();
            List<ReservedLinePrice> prices = prices();
            assertThrows(BusinessValidationException.class, () -> pending.confirmStock(prices));
        }

        @Test
        @DisplayName("rejectForStock() moves to STOCK_REJECTED with the reason")
        void rejectForStock() throws BusinessValidationException {
            Order created = Order.create(medicalSalesRepId, lines);
            created.pullDomainEvents();

            Order rejected = created.rejectForStock("Insufficient stock for product " + productB.value());

            assertEquals(OrderStatus.STOCK_REJECTED, rejected.status());
            assertNotNull(rejected.rejectedAt());
            assertTrue(rejected.rejectionReason().startsWith("Insufficient stock"));
            var events = rejected.pullDomainEvents();
            assertEquals(1, events.size());
            assertInstanceOf(OrderStockRejectedEvent.class, events.get(0));
        }

        @Test
        @DisplayName("rejectForStock() refuses an order that is not AWAITING_STOCK")
        void rejectForStockOnlyFromAwaitingStock() throws BusinessValidationException {
            Order pending = pending();
            assertThrows(BusinessValidationException.class, () -> pending.rejectForStock("late"));
        }
    }

    @Nested
    @DisplayName("Approval workflow")
    class ApprovalWorkflow {

        @Test
        @DisplayName("full happy path: AWAITING_STOCK -> PENDING_APPROVAL -> APPROVED -> DELIVERED")
        void fullHappyPath() throws BusinessValidationException {
            Order approved = pending().approve("admin@pharmalab.com");
            assertEquals(OrderStatus.APPROVED, approved.status());
            assertEquals("admin@pharmalab.com", approved.approvedBy());

            Order delivered = approved.markDelivered(null);
            assertEquals(OrderStatus.DELIVERED, delivered.status());
            assertNotNull(delivered.deliveredAt());
        }

        @Test
        @DisplayName("approve() should refuse an order still AWAITING_STOCK")
        void approveShouldRejectAwaitingStock() throws BusinessValidationException {
            Order created = Order.create(medicalSalesRepId, lines);
            assertThrows(BusinessValidationException.class, () -> created.approve("someone"));
        }

        @Test
        @DisplayName("reject() should transition PENDING_APPROVAL -> REJECTED and record OrderRejectedEvent")
        void rejectShouldTransitionToRejected() throws BusinessValidationException {
            Order rejected = pending().reject("admin@pharmalab.com", "Out of budget");

            assertEquals(OrderStatus.REJECTED, rejected.status());
            assertEquals("Out of budget", rejected.rejectionReason());
            var events = rejected.pullDomainEvents();
            assertEquals(1, events.size());
            assertInstanceOf(OrderRejectedEvent.class, events.get(0));
        }

        @Test
        @DisplayName("reject() should refuse an order still AWAITING_STOCK")
        void rejectShouldRejectAwaitingStock() throws BusinessValidationException {
            Order created = Order.create(medicalSalesRepId, lines);
            assertThrows(BusinessValidationException.class, () -> created.reject("someone", "reason"));
        }

        @Test
        @DisplayName("markDelivered() should reject a non-APPROVED order")
        void markDeliveredShouldRejectNonApproved() throws BusinessValidationException {
            Order created = Order.create(medicalSalesRepId, lines);
            assertThrows(BusinessValidationException.class, () -> created.markDelivered(null));
        }

        @Test
        @DisplayName("markDelivered() should be idempotent when already DELIVERED")
        void markDeliveredShouldBeIdempotent() throws BusinessValidationException {
            Order delivered = pending().approve("admin").markDelivered(null);
            delivered.pullDomainEvents(); // drain

            Order sameState = delivered.markDelivered(null);
            assertSame(delivered, sameState);
            assertTrue(sameState.pullDomainEvents().isEmpty());
        }
    }

    @Nested
    @DisplayName("OrderLine value object invariants")
    class OrderLineInvariants {

        @Test
        @DisplayName("OrderLineQuantity should reject zero or negative")
        void quantityShouldRejectNonPositive() {
            assertThrows(BusinessValidationException.class, () -> new OrderLineQuantity(0));
            assertThrows(BusinessValidationException.class, () -> new OrderLineQuantity(-1));
        }

        @Test
        @DisplayName("OrderLineUnitPrice should reject negative")
        void unitPriceShouldRejectNegative() {
            assertThrows(BusinessValidationException.class, () -> new OrderLineUnitPrice(new BigDecimal("-0.01")));
        }

        @Test
        @DisplayName("an unpriced line has no total")
        void unpricedLineHasNoTotal() throws BusinessValidationException {
            OrderLine line = OrderLine.unpriced(productA, new OrderLineQuantity(3));
            assertNull(line.lineTotal());
            assertEquals(0, new BigDecimal("4.50").compareTo(
                    line.priced("X", new OrderLineUnitPrice(new BigDecimal("1.50"))).lineTotal()));
        }
    }

    @Nested
    @DisplayName("domain events across chained transitions")
    class ChainedTransitionEvents {

        @Test
        @DisplayName("create → confirmStock keeps both events, in order")
        void createThenConfirmKeepsTheCreatedEvent() throws BusinessValidationException {
            Order submitted = Order.create(medicalSalesRepId, lines).confirmStock(prices());

            List<OrderDomainEvent> events = submitted.pullDomainEvents();

            assertEquals(2, events.size());
            assertInstanceOf(OrderCreatedEvent.class, events.get(0));
            assertInstanceOf(OrderSubmittedForApprovalEvent.class, events.get(1));
        }
    }
}

package com.das.cleanddd.domain.order.entities;

import java.math.BigDecimal;
import java.util.List;

import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Order Aggregate Root")
class OrderTest {

    private MedicalSalesRepId medicalSalesRepId;
    private List<OrderLine> lines;

    @BeforeEach
    void setUp() throws BusinessValidationException {
        medicalSalesRepId = new MedicalSalesRepId(java.util.UUID.randomUUID().toString());
        OrderLine line = new OrderLine(null, new ProductId(java.util.UUID.randomUUID().toString()),
                "Amoxicillin 500mg", new OrderLineQuantity(10), new OrderLineUnitPrice(new BigDecimal("12.50")));
        lines = List.of(line);
    }

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("static create() should default to CREATED and record OrderCreatedEvent")
        void shouldCreateInCreatedStatus() throws BusinessValidationException {
            Order order = Order.create(medicalSalesRepId, lines);

            assertEquals(OrderStatus.CREATED, order.status());
            assertEquals(1, order.lines().size());
            var events = order.pullDomainEvents();
            assertEquals(1, events.size());
            assertInstanceOf(com.das.cleanddd.domain.order.events.OrderCreatedEvent.class, events.get(0));
        }

        @Test
        @DisplayName("should reject an empty line list")
        void shouldRejectEmptyLines() {
            assertThrows(BusinessValidationException.class,
                    () -> new Order(null, medicalSalesRepId, List.of(), OrderStatus.CREATED,
                            null, null, null, null, null, null, null));
        }

        @Test
        @DisplayName("should reject a null medicalSalesRepId")
        void shouldRejectNullMsr() {
            assertThrows(BusinessValidationException.class,
                    () -> new Order(null, null, lines, OrderStatus.CREATED,
                            null, null, null, null, null, null, null));
        }

        @Test
        @DisplayName("totalAmount() should sum every line's total")
        void totalAmountShouldSumLines() throws BusinessValidationException {
            Order order = Order.create(medicalSalesRepId, lines);
            assertEquals(0, new BigDecimal("125.00").compareTo(order.totalAmount()));
        }
    }

    @Nested
    @DisplayName("Approval workflow")
    class ApprovalWorkflow {

        @Test
        @DisplayName("full happy path: CREATED -> PENDING_APPROVAL -> APPROVED -> DELIVERED")
        void fullHappyPath() throws BusinessValidationException {
            Order created = Order.create(medicalSalesRepId, lines);
            Order pending = created.submitForApproval();
            assertEquals(OrderStatus.PENDING_APPROVAL, pending.status());

            Order approved = pending.approve("admin@pharmalab.com");
            assertEquals(OrderStatus.APPROVED, approved.status());
            assertEquals("admin@pharmalab.com", approved.approvedBy());

            Order delivered = approved.markDelivered(null);
            assertEquals(OrderStatus.DELIVERED, delivered.status());
            assertNotNull(delivered.deliveredAt());
        }

        @Test
        @DisplayName("submitForApproval() should reject a non-CREATED order")
        void submitShouldRejectNonCreated() throws BusinessValidationException {
            Order pending = Order.create(medicalSalesRepId, lines).submitForApproval();
            assertThrows(BusinessValidationException.class, pending::submitForApproval);
        }

        @Test
        @DisplayName("approve() should reject a non-PENDING_APPROVAL order")
        void approveShouldRejectNonPending() throws BusinessValidationException {
            Order created = Order.create(medicalSalesRepId, lines);
            assertThrows(BusinessValidationException.class, () -> created.approve("someone"));
        }

        @Test
        @DisplayName("reject() should transition PENDING_APPROVAL -> REJECTED and record OrderRejectedEvent")
        void rejectShouldTransitionToRejected() throws BusinessValidationException {
            Order pending = Order.create(medicalSalesRepId, lines).submitForApproval();
            Order rejected = pending.reject("admin@pharmalab.com", "Out of budget");

            assertEquals(OrderStatus.REJECTED, rejected.status());
            assertEquals("Out of budget", rejected.rejectionReason());
            var events = rejected.pullDomainEvents();
            assertEquals(1, events.size());
            assertInstanceOf(com.das.cleanddd.domain.order.events.OrderRejectedEvent.class, events.get(0));
        }

        @Test
        @DisplayName("reject() should reject a non-PENDING_APPROVAL order")
        void rejectShouldRejectNonPending() throws BusinessValidationException {
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
            Order delivered = Order.create(medicalSalesRepId, lines)
                    .submitForApproval().approve("admin").markDelivered(null);
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
    }
}

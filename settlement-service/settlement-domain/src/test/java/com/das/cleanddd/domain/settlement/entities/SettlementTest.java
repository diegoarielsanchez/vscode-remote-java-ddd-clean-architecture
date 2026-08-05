package com.das.cleanddd.domain.settlement.entities;

import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Settlement Aggregate Root")
class SettlementTest {

    private static final LocalDate VALID_ISSUE_DATE = LocalDate.now().minusDays(30);
    private static final LocalDate VALID_DUE_DATE   = VALID_ISSUE_DATE.plusDays(30);
    private static final LocalDate SETTLEMENT_DATE  = LocalDate.now();

    private static final String MSR_UUID = "123e4567-e89b-12d3-a456-426614174000";

    private MedicalSalesRepId msrId;
    private InvoiceNumber invNumber;

    @BeforeEach
    void setUp() {
        msrId     = new MedicalSalesRepId(MSR_UUID);
        invNumber = new InvoiceNumber("A000100000001");
    }

    // ── creation ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create an OPEN settlement with a random id")
        void shouldCreateOpenSettlement() throws BusinessValidationException {
            Settlement s = Settlement.create("Monthly settlement", new SettlementDate(SETTLEMENT_DATE), msrId);

            assertNotNull(s.settlementId());
            assertEquals("Monthly settlement", s.description());
            assertEquals(SETTLEMENT_DATE, s.settlementDate().value());
            assertEquals(Settlement.SettlementStatus.OPEN, s.status());
            assertTrue(s.invoices().isEmpty());
            assertEquals(msrId, s.medicalSalesRepId());
        }

        @Test
        @DisplayName("should strip leading/trailing spaces from description")
        void shouldStripDescription() throws BusinessValidationException {
            Settlement s = new Settlement(null, "  trimmed  ", new SettlementDate(SETTLEMENT_DATE),
                    Settlement.SettlementStatus.OPEN, null, msrId);
            assertEquals("trimmed", s.description());
        }

        @Test
        @DisplayName("should throw when description is null")
        void shouldThrowWhenDescriptionNull() {
            assertThrows(BusinessValidationException.class,
                    () -> Settlement.create(null, new SettlementDate(SETTLEMENT_DATE), msrId));
        }

        @Test
        @DisplayName("should throw when description is blank")
        void shouldThrowWhenDescriptionBlank() {
            assertThrows(BusinessValidationException.class,
                    () -> Settlement.create("   ", new SettlementDate(SETTLEMENT_DATE), msrId));
        }

        @Test
        @DisplayName("should throw when settlement date is null")
        void shouldThrowWhenDateNull() {
            assertThrows(BusinessValidationException.class,
                    () -> Settlement.create("desc", (SettlementDate) null, msrId));
        }

        @Test
        @DisplayName("should throw when medicalSalesRepId is null")
        void shouldThrowWhenMsrIdNull() {
            assertThrows(BusinessValidationException.class,
                    () -> Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), null));
        }

        @Test
        @DisplayName("should default status to OPEN when null is provided")
        void shouldDefaultStatusToOpen() throws BusinessValidationException {
            Settlement s = new Settlement(null, "desc", new SettlementDate(SETTLEMENT_DATE), null, null, msrId);
            assertEquals(Settlement.SettlementStatus.OPEN, s.status());
        }
    }

    // ── invoice management ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Invoice management")
    class InvoiceManagement {

        @Test
        @DisplayName("addInvoice should add a new invoice to an OPEN settlement")
        void shouldAddInvoiceToOpenSettlement() throws BusinessValidationException {
            Settlement s = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId);
            Invoice added = s.addInvoice(invNumber, new IssueDate(VALID_ISSUE_DATE), new DueDate(VALID_DUE_DATE), new InvoiceAmount(new BigDecimal("500.00")));

            assertEquals(1, s.invoices().size());
            assertEquals(Invoice.InvoiceStatus.DRAFT, added.status());
            assertEquals(invNumber, added.invoiceNumber());
        }

        @Test
        @DisplayName("addInvoice should throw on duplicate invoice number")
        void shouldThrowOnDuplicateInvoiceNumber() throws BusinessValidationException {
            Settlement s = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId);
            s.addInvoice(invNumber, new IssueDate(VALID_ISSUE_DATE), null, new InvoiceAmount(BigDecimal.ZERO));

            assertThrows(BusinessValidationException.class,
                    () -> s.addInvoice(invNumber, new IssueDate(VALID_ISSUE_DATE), null, new InvoiceAmount(BigDecimal.ZERO)));
        }

        @Test
        @DisplayName("addInvoice should throw when settlement is CLOSED")
        void shouldThrowWhenAddingToClosedSettlement() throws BusinessValidationException {
            Settlement closed = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId).close();
            assertThrows(BusinessValidationException.class,
                    () -> closed.addInvoice(invNumber, new IssueDate(VALID_ISSUE_DATE), null, new InvoiceAmount(BigDecimal.ZERO)));
        }

        @Test
        @DisplayName("removeInvoice should remove an existing invoice")
        void shouldRemoveInvoice() throws BusinessValidationException {
            Settlement s = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId);
            Invoice added = s.addInvoice(invNumber, new IssueDate(VALID_ISSUE_DATE), null, new InvoiceAmount(BigDecimal.ZERO));
            s.removeInvoice(added);
            assertTrue(s.invoices().isEmpty());
        }

        @Test
        @DisplayName("removeInvoice should throw when settlement is CLOSED")
        void shouldThrowWhenRemovingFromClosedSettlement() throws BusinessValidationException {
            Settlement open   = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId);
            Invoice added     = open.addInvoice(invNumber, new IssueDate(VALID_ISSUE_DATE), null, new InvoiceAmount(BigDecimal.ZERO));
            Settlement closed = open.close();

            assertThrows(BusinessValidationException.class,
                    () -> closed.removeInvoice(added));
        }

        @Test
        @DisplayName("invoices() should return an unmodifiable view")
        void invoiceListShouldBeUnmodifiable() throws BusinessValidationException {
            Settlement s = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId);
            assertThrows(UnsupportedOperationException.class,
                    () -> s.invoices().add(null));
        }
    }

    // ── totalAmount ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Total amount")
    class TotalAmount {

        @Test
        @DisplayName("should be zero when there are no invoices")
        void shouldBeZeroWithNoInvoices() throws BusinessValidationException {
            Settlement s = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId);
            assertEquals(BigDecimal.ZERO, s.totalAmount());
        }

        @Test
        @DisplayName("should sum all invoice amounts")
        void shouldSumInvoiceAmounts() throws BusinessValidationException {
            Settlement s = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId);
            s.addInvoice(new InvoiceNumber("A000100000001"), new IssueDate(VALID_ISSUE_DATE), null, new InvoiceAmount(new BigDecimal("300.00")));
            s.addInvoice(new InvoiceNumber("A000100000002"), new IssueDate(VALID_ISSUE_DATE), null, new InvoiceAmount(new BigDecimal("200.50")));

            assertEquals(new BigDecimal("500.50"), s.totalAmount());
        }
    }

    // ── close ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Close settlement")
    class CloseSettlement {

        @Test
        @DisplayName("close() should transition OPEN → CLOSED")
        void shouldCloseOpenSettlement() throws BusinessValidationException {
            Settlement closed = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId).close();
            assertEquals(Settlement.SettlementStatus.CLOSED, closed.status());
        }

        @Test
        @DisplayName("close() should return a new immutable instance preserving invoices")
        void closeShouldPreserveInvoices() throws BusinessValidationException {
            Settlement open = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId);
            open.addInvoice(invNumber, new IssueDate(VALID_ISSUE_DATE), null, new InvoiceAmount(new BigDecimal("100.00")));
            Settlement closed = open.close();

            assertNotSame(open, closed);
            assertEquals(1, closed.invoices().size());
        }

        @Test
        @DisplayName("close() should throw when already CLOSED")
        void shouldThrowWhenAlreadyClosed() throws BusinessValidationException {
            Settlement closed = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId).close();
            assertThrows(BusinessValidationException.class, closed::close);
        }

        @Test
        @DisplayName("close() should preserve settlement id")
        void shouldPreserveIdOnClose() throws BusinessValidationException {
            Settlement open = Settlement.create("desc", new SettlementDate(SETTLEMENT_DATE), msrId);
            SettlementId id = open.settlementId();
            Settlement closed = open.close();
            assertEquals(id, closed.settlementId());
        }
    }
}

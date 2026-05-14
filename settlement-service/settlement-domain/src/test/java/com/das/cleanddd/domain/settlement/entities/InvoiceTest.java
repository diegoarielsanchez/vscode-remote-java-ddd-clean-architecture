package com.das.cleanddd.domain.settlement.entities;

import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Invoice Entity")
class InvoiceTest {

    // Issue date must be at least 60 days in the past
    private static final LocalDate VALID_ISSUE_DATE = LocalDate.now().minusDays(61);
    private static final LocalDate VALID_DUE_DATE   = VALID_ISSUE_DATE.plusDays(30);
    private static final BigDecimal AMOUNT          = new BigDecimal("1500.00");
    private static final InvoiceNumber INV_NUMBER   = new InvoiceNumber("A000100000001");

    // ── construction ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create a DRAFT invoice with all valid fields")
        void shouldCreateDraftInvoice() throws BusinessValidationException {
            Invoice invoice = new Invoice(
                    InvoiceId.random(), INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT, null);

            assertNotNull(invoice.invoiceId());
            assertEquals(INV_NUMBER, invoice.invoiceNumber());
            assertEquals(VALID_ISSUE_DATE, invoice.issueDate());
            assertEquals(VALID_DUE_DATE,   invoice.dueDate());
            assertEquals(AMOUNT,           invoice.amount());
            assertEquals(Invoice.InvoiceStatus.DRAFT, invoice.status());
        }

        @Test
        @DisplayName("should generate a random id when invoiceId is null")
        void shouldGenerateIdWhenNull() throws BusinessValidationException {
            Invoice invoice = new Invoice(
                    null, INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT, null);
            assertNotNull(invoice.invoiceId());
        }

        @Test
        @DisplayName("should accept a null due date")
        void shouldAcceptNullDueDate() {
            assertDoesNotThrow(() ->
                    new Invoice(null, INV_NUMBER, VALID_ISSUE_DATE, null, AMOUNT, null));
        }

        @Test
        @DisplayName("should throw when issue date is null")
        void shouldThrowWhenIssueDateNull() {
            BusinessValidationException ex = assertThrows(
                    BusinessValidationException.class,
                    () -> new Invoice(null, INV_NUMBER, null, null, AMOUNT, null));
            assertTrue(ex.getMessage().contains("Issue date is required."));
        }

        @Test
        @DisplayName("should throw when issue date is too recent (less than 60 days ago)")
        void shouldThrowWhenIssueDateTooRecent() {
            assertThrows(BusinessValidationException.class,
                    () -> new Invoice(null, INV_NUMBER,
                            LocalDate.now().minusDays(30), VALID_DUE_DATE, AMOUNT, null));
        }

        @Test
        @DisplayName("should throw when due date is before issue date")
        void shouldThrowWhenDueDateBeforeIssueDate() {
            assertThrows(BusinessValidationException.class,
                    () -> new Invoice(null, INV_NUMBER,
                            VALID_ISSUE_DATE, VALID_ISSUE_DATE.minusDays(1), AMOUNT, null));
        }

        @Test
        @DisplayName("should throw when amount is null")
        void shouldThrowWhenAmountNull() {
            assertThrows(BusinessValidationException.class,
                    () -> new Invoice(null, INV_NUMBER, VALID_ISSUE_DATE, null, null, null));
        }

        @Test
        @DisplayName("should throw when amount is negative")
        void shouldThrowWhenAmountNegative() {
            assertThrows(BusinessValidationException.class,
                    () -> new Invoice(null, INV_NUMBER, VALID_ISSUE_DATE, null,
                            new BigDecimal("-0.01"), null));
        }

        @Test
        @DisplayName("should accept zero amount")
        void shouldAcceptZeroAmount() {
            assertDoesNotThrow(() ->
                    new Invoice(null, INV_NUMBER, VALID_ISSUE_DATE, null, BigDecimal.ZERO, null));
        }
    }

    // ── state transitions ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("State transitions")
    class StateTransitions {

        private Invoice draftInvoice;

        @BeforeEach
        void setUp() throws BusinessValidationException {
            draftInvoice = new Invoice(
                    InvoiceId.random(), INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT, null);
        }

        @Test
        @DisplayName("issue() should transition DRAFT → ISSUED")
        void shouldIssueDraftInvoice() throws BusinessValidationException {
            Invoice issued = draftInvoice.issue();
            assertEquals(Invoice.InvoiceStatus.ISSUED, issued.status());
        }

        @Test
        @DisplayName("issue() should return a new immutable instance")
        void issueShouldReturnNewInstance() throws BusinessValidationException {
            Invoice issued = draftInvoice.issue();
            assertNotSame(draftInvoice, issued);
            assertEquals(Invoice.InvoiceStatus.DRAFT, draftInvoice.status()); // original unchanged
        }

        @Test
        @DisplayName("issue() should throw when invoice is not DRAFT")
        void shouldThrowWhenIssuingNonDraft() throws BusinessValidationException {
            Invoice issued = draftInvoice.issue();
            assertThrows(BusinessValidationException.class, issued::issue);
        }

        @Test
        @DisplayName("pay() should transition ISSUED → PAID")
        void shouldPayIssuedInvoice() throws BusinessValidationException {
            Invoice paid = draftInvoice.issue().pay();
            assertEquals(Invoice.InvoiceStatus.PAID, paid.status());
        }

        @Test
        @DisplayName("pay() should throw when invoice is not ISSUED")
        void shouldThrowWhenPayingNonIssued() {
            assertThrows(BusinessValidationException.class, draftInvoice::pay);
        }

        @Test
        @DisplayName("cancel() should transition DRAFT → CANCELLED")
        void shouldCancelDraftInvoice() throws BusinessValidationException {
            Invoice cancelled = draftInvoice.cancel();
            assertEquals(Invoice.InvoiceStatus.CANCELLED, cancelled.status());
        }

        @Test
        @DisplayName("cancel() should transition ISSUED → CANCELLED")
        void shouldCancelIssuedInvoice() throws BusinessValidationException {
            Invoice cancelled = draftInvoice.issue().cancel();
            assertEquals(Invoice.InvoiceStatus.CANCELLED, cancelled.status());
        }

        @Test
        @DisplayName("cancel() should throw when invoice is PAID")
        void shouldThrowWhenCancellingPaidInvoice() throws BusinessValidationException {
            Invoice paid = draftInvoice.issue().pay();
            assertThrows(BusinessValidationException.class, paid::cancel);
        }

        @Test
        @DisplayName("state transitions should preserve invoice id")
        void shouldPreserveIdOnTransition() throws BusinessValidationException {
            InvoiceId id = InvoiceId.random();
            Invoice invoice = new Invoice(id, INV_NUMBER, VALID_ISSUE_DATE, null, AMOUNT, null);
            assertEquals(id, invoice.issue().invoiceId());
        }
    }

    // ── invoiceFile property ──────────────────────────────────────────────────

    @Nested
    @DisplayName("InvoiceFile attachment")
    class InvoiceFileAttachment {

        private static final byte[]      FILE_BYTES    = new byte[]{1, 2, 3, 4, 5};
        private static final InvoiceFile INVOICE_FILE  =
                new InvoiceFile("INV-001.pdf", "application/pdf", FILE_BYTES);

        @Test
        @DisplayName("invoiceFile() should be null when invoice is created without a file")
        void invoiceFileShouldBeNullByDefault() throws BusinessValidationException {
            Invoice invoice = new Invoice(
                    InvoiceId.random(), INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT, null);
            assertNull(invoice.invoiceFile());
        }

        @Test
        @DisplayName("should store and return the InvoiceFile when provided at construction")
        void shouldReturnInvoiceFileWhenProvidedAtConstruction() throws BusinessValidationException {
            Invoice invoice = new Invoice(
                    InvoiceId.random(), INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT,
                    Invoice.InvoiceStatus.DRAFT, INVOICE_FILE);
            assertEquals(INVOICE_FILE, invoice.invoiceFile());
        }

        @Test
        @DisplayName("attachFile() should return a new invoice instance with the file attached")
        void attachFileShouldReturnNewInstanceWithFile() throws BusinessValidationException {
            Invoice invoice = new Invoice(
                    InvoiceId.random(), INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT, null);

            Invoice withFile = invoice.attachFile(INVOICE_FILE);

            assertNotSame(invoice, withFile);
            assertNull(invoice.invoiceFile());            // original is unchanged
            assertEquals(INVOICE_FILE, withFile.invoiceFile());
        }

        @Test
        @DisplayName("attachFile() should preserve all other invoice fields")
        void attachFileShouldPreserveOtherFields() throws BusinessValidationException {
            InvoiceId id = InvoiceId.random();
            Invoice invoice = new Invoice(
                    id, INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT, null);

            Invoice withFile = invoice.attachFile(INVOICE_FILE);

            assertEquals(id,              withFile.invoiceId());
            assertEquals(INV_NUMBER,      withFile.invoiceNumber());
            assertEquals(VALID_ISSUE_DATE, withFile.issueDate());
            assertEquals(VALID_DUE_DATE,  withFile.dueDate());
            assertEquals(AMOUNT,          withFile.amount());
            assertEquals(Invoice.InvoiceStatus.DRAFT, withFile.status());
        }

        @Test
        @DisplayName("attachFile(null) should throw BusinessValidationException")
        void attachFileNullShouldThrow() throws BusinessValidationException {
            Invoice invoice = new Invoice(
                    InvoiceId.random(), INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT, null);
            assertThrows(BusinessValidationException.class, () -> invoice.attachFile(null));
        }

        @Test
        @DisplayName("issue() should carry the attached InvoiceFile to the new instance")
        void issueShouldCarryInvoiceFile() throws BusinessValidationException {
            Invoice invoice = new Invoice(
                    InvoiceId.random(), INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT,
                    Invoice.InvoiceStatus.DRAFT, INVOICE_FILE);

            Invoice issued = invoice.issue();

            assertEquals(INVOICE_FILE, issued.invoiceFile());
        }

        @Test
        @DisplayName("pay() should carry the attached InvoiceFile to the new instance")
        void payShouldCarryInvoiceFile() throws BusinessValidationException {
            Invoice invoice = new Invoice(
                    InvoiceId.random(), INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT,
                    Invoice.InvoiceStatus.DRAFT, INVOICE_FILE);

            Invoice paid = invoice.issue().pay();

            assertEquals(INVOICE_FILE, paid.invoiceFile());
        }

        @Test
        @DisplayName("cancel() should carry the attached InvoiceFile to the new instance")
        void cancelShouldCarryInvoiceFile() throws BusinessValidationException {
            Invoice invoice = new Invoice(
                    InvoiceId.random(), INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT,
                    Invoice.InvoiceStatus.DRAFT, INVOICE_FILE);

            Invoice cancelled = invoice.cancel();

            assertEquals(INVOICE_FILE, cancelled.invoiceFile());
        }

        @Test
        @DisplayName("attachFile() can replace an existing file with a new one")
        void attachFileShouldReplaceExistingFile() throws BusinessValidationException {
            Invoice invoice = new Invoice(
                    InvoiceId.random(), INV_NUMBER, VALID_ISSUE_DATE, VALID_DUE_DATE, AMOUNT,
                    Invoice.InvoiceStatus.DRAFT, INVOICE_FILE);

            InvoiceFile newFile = new InvoiceFile("INV-001-v2.pdf", "application/pdf", new byte[]{9, 8, 7});
            Invoice updated = invoice.attachFile(newFile);

            assertEquals(newFile, updated.invoiceFile());
            assertEquals(INVOICE_FILE, invoice.invoiceFile()); // original unchanged
        }
    }
}

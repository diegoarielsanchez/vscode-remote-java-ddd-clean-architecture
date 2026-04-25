package com.das.cleanddd.domain.settlement.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

public final class Invoice {

    public enum InvoiceStatus {
        DRAFT, ISSUED, PAID, CANCELLED
    }

    private final InvoiceId      _invoiceId;
    private final InvoiceNumber   _invoiceNumber;
    private final LocalDate _issueDate;
    private final LocalDate _dueDate;
    private final BigDecimal _amount;
    private InvoiceStatus   _status;

    public Invoice(InvoiceId invoiceId,
                   InvoiceNumber invoiceNumber,
                   LocalDate issueDate,
                   LocalDate dueDate,
                   BigDecimal amount,
                   InvoiceStatus status) throws BusinessValidationException {

        if (issueDate == null) {
            throw new BusinessValidationException("Issue date is required.");
        }
        if (issueDate.isAfter(LocalDate.now().minusDays(60))) {
            throw new BusinessValidationException("Issue date must be at least 60 days in the past.");
        }
        if (dueDate != null && dueDate.isBefore(issueDate)) {
            throw new BusinessValidationException("Due date cannot be before issue date.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessValidationException("Amount must be zero or positive.");
        }

        this._invoiceId     = invoiceId == null ? InvoiceId.random() : invoiceId;
        this._invoiceNumber = invoiceNumber;
        this._issueDate     = issueDate;
        this._dueDate       = dueDate;
        this._amount        = amount;
        this._status        = status == null ? InvoiceStatus.DRAFT : status;
    }

    @SuppressWarnings("unused")
    private Invoice() {
        this._invoiceId     = null;
        this._invoiceNumber = null;
        this._issueDate     = null;
        this._dueDate       = null;
        this._amount        = null;
        this._status        = null;
    }

    static Invoice create(InvoiceNumber invoiceNumber,
                           LocalDate issueDate,
                           LocalDate dueDate,
                           BigDecimal amount) throws BusinessValidationException {
        return new Invoice(InvoiceId.random(), invoiceNumber, issueDate, dueDate, amount, InvoiceStatus.DRAFT);
    }

    // ── Queries ────────────────────────────────────────────────────────────

    public InvoiceId invoiceId()     { return _invoiceId; }
    public InvoiceNumber invoiceNumber()    { return _invoiceNumber; }
    public LocalDate issueDate()     { return _issueDate; }
    public LocalDate dueDate()       { return _dueDate; }
    public BigDecimal amount()       { return _amount; }
    public InvoiceStatus status()    { return _status; }

    // ── State transitions ──────────────────────────────────────────────────

    public Invoice issue() throws BusinessValidationException {
        if (_status != InvoiceStatus.DRAFT) {
            throw new BusinessValidationException("Only DRAFT invoices can be issued.");
        }
        return new Invoice(_invoiceId, _invoiceNumber, _issueDate, _dueDate, _amount, InvoiceStatus.ISSUED);
    }

    public Invoice pay() throws BusinessValidationException {
        if (_status != InvoiceStatus.ISSUED) {
            throw new BusinessValidationException("Only ISSUED invoices can be marked as paid.");
        }
        return new Invoice(_invoiceId, _invoiceNumber, _issueDate, _dueDate, _amount, InvoiceStatus.PAID);
    }

    public Invoice cancel() throws BusinessValidationException {
        if (_status == InvoiceStatus.PAID) {
            throw new BusinessValidationException("PAID invoices cannot be cancelled.");
        }
        return new Invoice(_invoiceId, _invoiceNumber, _issueDate, _dueDate, _amount, InvoiceStatus.CANCELLED);
    }

    // ── Object identity ───────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Invoice other)) return false;
        return Objects.equals(_invoiceId, other._invoiceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_invoiceId);
    }

    @Override
    public String toString() {
        return "Invoice{id=" + _invoiceId + ", number='" + _invoiceNumber.value() + "', status=" + _status + '}';
    }
}

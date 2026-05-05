package com.das.cleanddd.domain.settlement.entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.das.cleanddd.domain.shared.AggregateRoot;
import com.das.cleanddd.domain.shared.exceptions.BusinessValidationException;

public final class Settlement extends AggregateRoot {

    public enum SettlementStatus {
        OPEN, CLOSED
    }

    private final SettlementId         _settlementId;
    private final String               _description;
    private final LocalDate            _settlementDate;
    private SettlementStatus           _status;
    private final List<Invoice>        _invoices;
    private final MedicalSalesRepId    _medicalSalesRepId;

    public Settlement(SettlementId settlementId,
                      String description,
                      LocalDate settlementDate,
                      SettlementStatus status,
                      List<Invoice> invoices,
                      MedicalSalesRepId medicalSalesRepId) throws BusinessValidationException {

        if (settlementDate == null) {
            throw new BusinessValidationException("Settlement date is required.");
        }
        if (description == null || description.isBlank()) {
            throw new BusinessValidationException("Settlement description is required.");
        }
        if (medicalSalesRepId == null) {
            throw new BusinessValidationException("Medical sales rep is required.");
        }

        this._settlementId      = settlementId == null ? SettlementId.random() : settlementId;
        this._description       = description.strip();
        this._settlementDate    = settlementDate;
        this._status            = status == null ? SettlementStatus.OPEN : status;
        this._invoices          = invoices == null ? new ArrayList<>() : new ArrayList<>(invoices);
        this._medicalSalesRepId = medicalSalesRepId;
    }

    @SuppressWarnings("unused")
    private Settlement() {
        this._settlementId      = null;
        this._description       = null;
        this._settlementDate    = null;
        this._status            = null;
        this._invoices          = new ArrayList<>();
        this._medicalSalesRepId = null;
    }

    public static Settlement create(String description,
                                    LocalDate settlementDate,
                                    MedicalSalesRepId medicalSalesRepId) throws BusinessValidationException {
        return new Settlement(SettlementId.random(), description, settlementDate, SettlementStatus.OPEN, null, medicalSalesRepId);
    }

    // ── Invoice membership ────────────────────────────────────────────────

    public Invoice addInvoice(InvoiceNumber invoiceNumber,
                               LocalDate issueDate,
                               LocalDate dueDate,
                               BigDecimal amount) throws BusinessValidationException {
        if (_status == SettlementStatus.CLOSED) {
            throw new BusinessValidationException("Cannot add invoices to a CLOSED settlement.");
        }
        boolean duplicate = _invoices.stream()
                .anyMatch(i -> i.invoiceNumber().equals(invoiceNumber));
        if (duplicate) {
            throw new BusinessValidationException("An invoice with number '" + invoiceNumber.value() + "' already exists in this settlement.");
        }
        Invoice invoice = Invoice.create(invoiceNumber, issueDate, dueDate, amount);
        _invoices.add(invoice);
        return invoice;
    }

    public void removeInvoice(Invoice invoice) throws BusinessValidationException {
        if (_status == SettlementStatus.CLOSED) {
            throw new BusinessValidationException("Cannot remove invoices from a CLOSED settlement.");
        }
        _invoices.remove(invoice);
    }

    // ── State transition ──────────────────────────────────────────────────

    public Settlement close() throws BusinessValidationException {
        if (_status == SettlementStatus.CLOSED) {
            throw new BusinessValidationException("Settlement is already CLOSED.");
        }
        return new Settlement(_settlementId, _description, _settlementDate, SettlementStatus.CLOSED, _invoices, _medicalSalesRepId);
    }

    // ── Computed value ────────────────────────────────────────────────────

    public BigDecimal totalAmount() {
        return _invoices.stream()
                .map(Invoice::amount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ── Queries ───────────────────────────────────────────────────────────

    public SettlementId settlementId()             { return _settlementId; }
    public String description()                    { return _description; }
    public LocalDate settlementDate()              { return _settlementDate; }
    public SettlementStatus status()               { return _status; }
    public List<Invoice> invoices()                { return Collections.unmodifiableList(_invoices); }
    public MedicalSalesRepId medicalSalesRepId()   { return _medicalSalesRepId; }

    // ── Object identity ───────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Settlement other)) return false;
        return Objects.equals(_settlementId, other._settlementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_settlementId);
    }

    @Override
    public String toString() {
        return "Settlement{id=" + _settlementId
                + ", description='" + _description
                + "', status=" + _status
                + ", invoices=" + _invoices.size() + '}';
    }
}

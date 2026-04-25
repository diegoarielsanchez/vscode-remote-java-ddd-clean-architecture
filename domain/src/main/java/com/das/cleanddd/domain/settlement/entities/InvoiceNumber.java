package com.das.cleanddd.domain.settlement.entities;

import com.das.cleanddd.domain.shared.StringValueObject;

/**
 * Value object representing an invoice number.
 *
 * Format: [type-letter][4-digit point-of-sales][8-digit invoice number]
 * Example: A000100000001
 * Total length: 13 characters.
 */
public final class InvoiceNumber extends StringValueObject {

    private static final String REGEX    = "^[A-Za-z]\\d{4}\\d{8}$";
    private static final String ERROR    =
            "Invoice number must follow the format: 1 letter (type) + 4 digits (point of sales) + 8 digits (invoice number). Example: A000100000001";

    public InvoiceNumber(String value) {
        super(value);
        validate(value);
    }

    private static void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Invoice number is required.");
        }
        if (!value.matches(REGEX)) {
            throw new IllegalArgumentException(ERROR);
        }
    }

    public char invoiceType() {
        return value().charAt(0);
    }

    public String pointOfSales() {
        return value().substring(1, 5);
    }

    public String number() {
        return value().substring(5);
    }
}

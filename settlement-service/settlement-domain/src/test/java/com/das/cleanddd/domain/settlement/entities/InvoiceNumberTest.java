package com.das.cleanddd.domain.settlement.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InvoiceNumber Value Object")
class InvoiceNumberTest {

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should create a valid invoice number")
    void shouldCreateValidInvoiceNumber() {
        InvoiceNumber inv = new InvoiceNumber("A000100000001");
        assertEquals("A000100000001", inv.value());
    }

    @Test
    @DisplayName("should parse invoice type letter")
    void shouldParseInvoiceType() {
        assertEquals('A', new InvoiceNumber("A000100000001").invoiceType());
        assertEquals('B', new InvoiceNumber("B000100000001").invoiceType());
    }

    @Test
    @DisplayName("should parse point of sales (4 digits after type)")
    void shouldParsePointOfSales() {
        assertEquals("0001", new InvoiceNumber("A000100000001").pointOfSales());
    }

    @Test
    @DisplayName("should parse invoice number segment (last 8 digits)")
    void shouldParseNumber() {
        assertEquals("00000001", new InvoiceNumber("A000100000001").number());
    }

    @Test
    @DisplayName("should accept lowercase type letter")
    void shouldAcceptLowercaseLetter() {
        assertDoesNotThrow(() -> new InvoiceNumber("a000100000001"));
    }

    // ── null / blank ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw when value is null")
    void shouldThrowWhenNull() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new InvoiceNumber(null));
        assertEquals("Invoice number is required.", ex.getMessage());
    }

    @Test
    @DisplayName("should throw when value is blank")
    void shouldThrowWhenBlank() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new InvoiceNumber("  "));
        assertEquals("Invoice number is required.", ex.getMessage());
    }

    // ── format constraints ────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw when too short")
    void shouldThrowWhenTooShort() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvoiceNumber("A00010000001")); // 12 chars, missing last digit
    }

    @Test
    @DisplayName("should throw when too long")
    void shouldThrowWhenTooLong() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvoiceNumber("A0001000000011")); // 14 chars
    }

    @Test
    @DisplayName("should throw when starts with a digit instead of a letter")
    void shouldThrowWhenStartsWithDigit() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvoiceNumber("1000100000001"));
    }

    @Test
    @DisplayName("should throw when contains non-numeric digits after the type letter")
    void shouldThrowWhenContainsNonNumericChars() {
        assertThrows(IllegalArgumentException.class,
                () -> new InvoiceNumber("AXXXX00000001"));
    }

    // ── equality ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("two InvoiceNumbers with the same value should be equal")
    void shouldBeEqual() {
        InvoiceNumber a = new InvoiceNumber("A000100000001");
        InvoiceNumber b = new InvoiceNumber("A000100000001");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

package com.das.cleanddd.domain.medicalsalesrep.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MedicalSalesRepName Value Object")
class MedicalSalesRepNameTest {

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should create a valid name")
    void shouldCreateValidName() {
        MedicalSalesRepName name = new MedicalSalesRepName("John");
        assertEquals("John", name.value());
    }

    @Test
    @DisplayName("should accept name with diacritics")
    void shouldAcceptDiacritics() {
        assertDoesNotThrow(() -> new MedicalSalesRepName("José"));
    }

    @Test
    @DisplayName("should accept name with internal space")
    void shouldAcceptNameWithSpace() {
        assertDoesNotThrow(() -> new MedicalSalesRepName("Mary Jane"));
    }

    @Test
    @DisplayName("should accept minimum length name (2 chars)")
    void shouldAcceptMinLengthName() {
        assertDoesNotThrow(() -> new MedicalSalesRepName("Jo"));
    }

    @Test
    @DisplayName("should accept name at maximum length (100 chars)")
    void shouldAcceptMaxLengthName() {
        String maxName = "A".repeat(MedicalSalesRepName.MAX_LENGTH);
        assertDoesNotThrow(() -> new MedicalSalesRepName(maxName));
    }

    // ── null / empty ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw when name is null")
    void shouldThrowWhenNull() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new MedicalSalesRepName(null));
        assertEquals(MedicalSalesRepName.ERROR_MESSAGE_NULL, ex.getMessage());
    }

    @Test
    @DisplayName("should throw when name is empty")
    void shouldThrowWhenEmpty() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new MedicalSalesRepName(""));
        assertEquals(MedicalSalesRepName.ERROR_MESSAGE_EMPTY, ex.getMessage());
    }

    // ── length constraints ────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw when name is too short (1 char)")
    void shouldThrowWhenTooShort() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new MedicalSalesRepName("A"));
        assertEquals(MedicalSalesRepName.ERROR_MESSAGE_INVALID, ex.getMessage());
    }

    @Test
    @DisplayName("should throw when name exceeds maximum length")
    void shouldThrowWhenTooLong() {
        String longName = "A".repeat(MedicalSalesRepName.MAX_LENGTH + 1);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new MedicalSalesRepName(longName));
        assertEquals(MedicalSalesRepName.ERROR_MESSAGE_INVALID, ex.getMessage());
    }

    // ── regex constraint ──────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw when name contains digits")
    void shouldThrowWhenContainsDigits() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new MedicalSalesRepName("John1"));
        assertEquals(MedicalSalesRepName.ERROR_MESSAGE_INVALID, ex.getMessage());
    }

    @Test
    @DisplayName("should throw when name contains special characters")
    void shouldThrowWhenContainsSpecialChars() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MedicalSalesRepName("John@Doe"));
    }
}

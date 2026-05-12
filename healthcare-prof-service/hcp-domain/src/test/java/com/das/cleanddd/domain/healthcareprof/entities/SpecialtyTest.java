package com.das.cleanddd.domain.healthcareprof.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Specialty Value Object")
class SpecialtyTest {

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should create a valid specialty")
    void shouldCreateValidSpecialty() {
        Specialty specialty = new Specialty("CARD", "Cardiology");
        assertEquals("CARD", specialty.code());
        assertEquals("Cardiology", specialty.name());
    }

    @Test
    @DisplayName("should accept name with diacritics and spaces")
    void shouldAcceptNameWithDiacriticsAndSpaces() {
        assertDoesNotThrow(() -> new Specialty("ENT", "Ear Nose Throat"));
    }

    @Test
    @DisplayName("should accept name with minimum length (2 chars)")
    void shouldAcceptMinLengthName() {
        assertDoesNotThrow(() -> new Specialty("GEN", "GP"));
    }

    // ── null / blank code ─────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw when code is null")
    void shouldThrowWhenCodeIsNull() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new Specialty(null, "Cardiology"));
        assertEquals("Code cannot be null or empty.", ex.getMessage());
    }

    @Test
    @DisplayName("should throw when code is blank")
    void shouldThrowWhenCodeIsBlank() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new Specialty("  ", "Cardiology"));
        assertEquals("Code cannot be null or empty.", ex.getMessage());
    }

    // ── null / empty name ─────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw when name is null")
    void shouldThrowWhenNameIsNull() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new Specialty("CARD", null));
        assertEquals(Specialty.ERROR_MESSAGE_NULL, ex.getMessage());
    }

    @Test
    @DisplayName("should throw when name is empty")
    void shouldThrowWhenNameIsEmpty() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new Specialty("CARD", ""));
        assertEquals(Specialty.ERROR_MESSAGE_EMPTY, ex.getMessage());
    }

    // ── length constraints ────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw when name is too short (1 char)")
    void shouldThrowWhenNameTooShort() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new Specialty("CARD", "A"));
        assertEquals(Specialty.ERROR_MESSAGE_INVALID, ex.getMessage());
    }

    @Test
    @DisplayName("should throw when name exceeds 100 characters")
    void shouldThrowWhenNameTooLong() {
        String longName = "A".repeat(Specialty.MAX_LENGTH + 1);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new Specialty("CARD", longName));
        assertEquals(Specialty.ERROR_MESSAGE_INVALID, ex.getMessage());
    }

    // ── regex constraint ──────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw when name contains digits")
    void shouldThrowWhenNameContainsDigits() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new Specialty("CARD", "Cardiology123"));
        assertEquals(Specialty.ERROR_MESSAGE_INVALID, ex.getMessage());
    }

    // ── equality (record) ─────────────────────────────────────────────────────

    @Test
    @DisplayName("two specialties with same code and name should be equal")
    void shouldBeEqualWhenSameCodeAndName() {
        Specialty a = new Specialty("CARD", "Cardiology");
        Specialty b = new Specialty("CARD", "Cardiology");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}

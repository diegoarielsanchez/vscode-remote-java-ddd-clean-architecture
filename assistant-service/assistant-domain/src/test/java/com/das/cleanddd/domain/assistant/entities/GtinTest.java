package com.das.cleanddd.domain.assistant.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Gtin")
class GtinTest {

    @Test
    @DisplayName("should store a GTIN-13 right-aligned in 14 digits, as GS1 recommends")
    void shouldPadToFourteenDigits() {
        assertEquals("09506000134352", new Gtin("9506000134352").value());
        assertEquals("09506000134352", new Gtin("09506000134352").value());
    }

    @Test
    @DisplayName("should accept a valid EAN-13")
    void shouldAcceptEan13() {
        assertEquals("04006381333931", new Gtin("4006381333931").value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"09506000134353", "123456789", "00000000000000", "95060001343ab", ""})
    @DisplayName("should reject a wrong check digit, a wrong length, all zeros or non-digits")
    void shouldReject(String value) {
        assertThrows(IllegalArgumentException.class, () -> new Gtin(value));
    }

    @Test
    @DisplayName("should reject null")
    void shouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> new Gtin(null));
    }
}

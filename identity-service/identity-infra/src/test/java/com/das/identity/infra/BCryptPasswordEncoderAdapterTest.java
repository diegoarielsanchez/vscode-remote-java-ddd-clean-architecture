package com.das.identity.infra;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the password hashing adapter (OWASP A02 — Cryptographic Failures).
 */
@DisplayName("BCryptPasswordEncoderAdapter")
class BCryptPasswordEncoderAdapterTest {

    private BCryptPasswordEncoderAdapter encoder;

    @BeforeEach
    void setUp() {
        encoder = new BCryptPasswordEncoderAdapter();
    }

    @Test
    @DisplayName("should produce a BCrypt hash at cost factor 12 (OWASP ASVS)")
    void shouldUseCostFactor12() {
        String hash = encoder.encode("Apatehia65$");

        assertTrue(hash.startsWith("$2a$12$"),
                "expected a cost-12 BCrypt hash but got: " + hash);
    }

    @Test
    @DisplayName("should never return the raw password as its own hash")
    void shouldNotReturnPlainText() {
        String raw = "Apatehia65$";

        assertNotEquals(raw, encoder.encode(raw));
    }

    @Test
    @DisplayName("should salt each hash so identical passwords hash differently")
    void shouldSaltEachHash() {
        String first  = encoder.encode("same-password");
        String second = encoder.encode("same-password");

        assertNotEquals(first, second,
                "identical hashes would mean the encoder is unsalted and vulnerable to rainbow tables");
    }

    @Test
    @DisplayName("should verify a password against its own hash")
    void shouldMatchCorrectPassword() {
        String hash = encoder.encode("Apatehia65$");

        assertTrue(encoder.matches("Apatehia65$", hash));
    }

    @Test
    @DisplayName("should reject an incorrect password")
    void shouldRejectWrongPassword() {
        String hash = encoder.encode("Apatehia65$");

        assertFalse(encoder.matches("wrong-password", hash));
    }

    @Test
    @DisplayName("should reject a password that differs only in case")
    void shouldBeCaseSensitive() {
        String hash = encoder.encode("Apatehia65$");

        assertFalse(encoder.matches("apatehia65$", hash));
    }

    @Test
    @DisplayName("should verify both independently salted hashes of the same password")
    void shouldMatchAcrossDistinctSalts() {
        String first  = encoder.encode("same-password");
        String second = encoder.encode("same-password");

        assertTrue(encoder.matches("same-password", first));
        assertTrue(encoder.matches("same-password", second));
    }
}

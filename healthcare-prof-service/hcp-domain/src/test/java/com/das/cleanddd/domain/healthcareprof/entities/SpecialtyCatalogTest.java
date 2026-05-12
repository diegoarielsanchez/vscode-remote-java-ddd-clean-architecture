package com.das.cleanddd.domain.healthcareprof.entities;

import com.das.cleanddd.domain.shared.exceptions.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpecialtyCatalog Domain Service")
class SpecialtyCatalogTest {

    // ── happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return correct specialty for a valid code")
    void shouldReturnSpecialtyForValidCode() throws DomainException {
        Specialty specialty = SpecialtyCatalog.fromCode("CARD");
        assertEquals("CARD", specialty.code());
        assertEquals("Cardiology", specialty.name());
    }

    @Test
    @DisplayName("should be case-insensitive for lookup")
    void shouldBeCaseInsensitive() throws DomainException {
        Specialty lower = SpecialtyCatalog.fromCode("card");
        Specialty upper = SpecialtyCatalog.fromCode("CARD");
        assertEquals(upper, lower);
    }

    @Test
    @DisplayName("should trim whitespace from code")
    void shouldTrimWhitespace() throws DomainException {
        Specialty specialty = SpecialtyCatalog.fromCode("  NEUR  ");
        assertEquals("NEUR", specialty.code());
        assertEquals("Neurology", specialty.name());
    }

    @Test
    @DisplayName("fromCode should return all known catalog codes without error")
    void shouldResolveKnownCatalogCodes() {
        // Verify each catalog code resolves successfully via fromCode
        List<String> knownCodes = List.of(
                "CARD", "DERM", "NEUR", "PED", "ORTH", "ONCO",
                "PSYC", "ODON", "OPHT", "GAST", "ENDO", "RHEU",
                "UROL", "GYNE", "NEPH", "HEM", "IMM", "INF",
                "RAD", "ANES", "PATH", "GEN");
        for (String code : knownCodes) {
            assertDoesNotThrow(() -> SpecialtyCatalog.fromCode(code),
                    "Expected no exception for catalog code: " + code);
        }
    }

    // ── invalid code ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("should throw DomainException for unknown code")
    void shouldThrowForUnknownCode() {
        DomainException ex = assertThrows(
                DomainException.class,
                () -> SpecialtyCatalog.fromCode("UNKNOWN"));
        assertTrue(ex.getMessage().contains("UNKNOWN"));
    }

    @Test
    @DisplayName("should throw DomainException for null code")
    void shouldThrowForNullCode() {
        assertThrows(DomainException.class, () -> SpecialtyCatalog.fromCode(null));
    }

    @Test
    @DisplayName("should throw DomainException for empty code")
    void shouldThrowForEmptyCode() {
        assertThrows(DomainException.class, () -> SpecialtyCatalog.fromCode(""));
    }
}

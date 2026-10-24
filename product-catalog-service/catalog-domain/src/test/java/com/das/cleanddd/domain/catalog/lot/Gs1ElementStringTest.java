package com.das.cleanddd.domain.catalog.lot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.das.cleanddd.domain.catalog.entities.Gtin;
import com.das.cleanddd.domain.catalog.lot.Gs1ElementString.Gs1Data;

@DisplayName("GS1 element string (pack DataMatrix)")
class Gs1ElementStringTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 24);
    private static final char GS = Gs1ElementString.GS;

    @Test
    @DisplayName("reads the human-readable form")
    void humanReadable() {
        Gs1Data d = Gs1ElementString.parse("(01)09506000134352(17)270331(10)L2026-07A(21)SN000123", TODAY);

        assertEquals(new Gtin("09506000134352"), d.gtin());
        assertEquals(LocalDate.of(2027, 3, 31), d.expirationDate());
        assertEquals("L2026-07A", d.batchNumber().value());
        assertEquals("SN000123", d.serialNumber());
    }

    @Test
    @DisplayName("reads raw scanner output: symbology identifier, fixed-length AIs, GS after variable ones")
    void rawScannerOutput() {
        Gs1Data d = Gs1ElementString.parse("]d20109506000134352" + "10L2026-07A" + GS + "17270331" + "21SN000123", TODAY);

        assertEquals("L2026-07A", d.batchNumber().value());
        assertEquals(LocalDate.of(2027, 3, 31), d.expirationDate());
        assertEquals("SN000123", d.serialNumber());
    }

    @Test
    @DisplayName("a batch with characters of GS1 set 82 is kept as is in the raw form")
    void cset82Batch() {
        assertEquals("A(1)/B_%", Gs1ElementString.parse("10A(1)/B_%", TODAY).batchNumber().value());
    }

    @Test
    @DisplayName("fields the code does not carry are null")
    void missingFields() {
        Gs1Data d = Gs1ElementString.parse("(10)L1", TODAY);
        assertNull(d.gtin());
        assertNull(d.expirationDate());
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "270300, 2027-03-31", // DD = 00: last day of the month
            "280200, 2028-02-29", // leap year
            "760101, 2076-01-01", // +50 years: still this century
            "770101, 1977-01-01", // +51: previous century
            "000101, 2000-01-01"
    })
    @DisplayName("YYMMDD follows the GS1 century rule and day 00")
    void dates(String yymmdd, LocalDate expected) {
        assertEquals(expected, Gs1ElementString.date(yymmdd, TODAY, "17"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(01)09506000134353",             // wrong GTIN check digit
            "(17)271332",                     // month 13
            "(17)270230",                     // 30 February
            "(17)2703",                       // too short
            "(99)ABC",                        // unsupported AI
            "(10)L1(10)L2",                   // repeated AI
            "(10)123456789012345678901",      // batch longer than 20
            "(10)LOT 1",                      // space is not in GS1 set 82
            "(10)L1 trailing(17)270331x",     // malformed
            "0109506000134",                  // raw GTIN cut short
            ""
    })
    @DisplayName("refuses malformed codes instead of guessing")
    void refusesMalformed(String code) {
        assertThrows(IllegalArgumentException.class, () -> Gs1ElementString.parse(code, TODAY));
    }
}

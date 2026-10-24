package com.das.cleanddd.domain.catalog.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("GTIN (GS1 AI 01)")
class GtinTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "09506000134352, 09506000134352", // GS1's GTIN-14 example
            "9506000134352,  09506000134352", // the same item as GTIN-13
            "4006381333931,  04006381333931", // EAN-13
            "036000291452,   00036000291452", // UPC-A (GTIN-12)
            "96385074,       00000096385074"  // EAN-8
    })
    @DisplayName("accepts GTIN-8/12/13/14 with a valid check digit and stores them as 14 digits")
    void normalisesToFourteenDigits(String input, String stored) {
        assertEquals(stored, new Gtin(input).value());
    }

    @Test
    @DisplayName("GTIN-13 and GTIN-14 of the same item are equal")
    void sameItemIsEqual() {
        assertEquals(new Gtin("9506000134352"), new Gtin("09506000134352"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"09506000134353", "4006381333932", "1234567", "123456789", "0950600013435A",
            " 9506000134352", "00000000000000", ""})
    @DisplayName("refuses a wrong check digit, wrong length, non-digits or all zeros")
    void refusesInvalid(String input) {
        assertThrows(IllegalArgumentException.class, () -> new Gtin(input));
    }
}

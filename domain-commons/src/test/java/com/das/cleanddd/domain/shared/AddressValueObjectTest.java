package com.das.cleanddd.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AddressValueObject")
class AddressValueObjectTest {

    private static AddressValueObject anAddress() {
        return new AddressValueObject("1600 Amphitheatre Pkwy", "Mountain View", "CA", "94043", "USA");
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should accept a fully populated address")
        void shouldAcceptFullAddress() {
            AddressValueObject address = anAddress();

            assertEquals("1600 Amphitheatre Pkwy", address.street());
            assertEquals("Mountain View", address.city());
            assertEquals("CA", address.state());
            assertEquals("94043", address.postalCode());
            assertEquals("USA", address.country());
        }

        @Test
        @DisplayName("should accept a null state — not every country has states/provinces")
        void shouldAcceptNullState() {
            AddressValueObject address = new AddressValueObject("Rue de Rivoli", "Paris", null, "75001", "France");

            assertNull(address.state());
        }

        @Test
        @DisplayName("should accept a blank state as absent")
        void shouldTreatBlankStateAsAbsent() {
            AddressValueObject address = new AddressValueObject("Rue de Rivoli", "Paris", "   ", "75001", "France");

            assertNull(address.state());
        }

        @Test
        @DisplayName("should strip surrounding whitespace from every field")
        void shouldStripWhitespace() {
            AddressValueObject address = new AddressValueObject(
                    "  Main St  ", "  Springfield  ", "  IL  ", "  62701  ", "  USA  ");

            assertEquals("Main St", address.street());
            assertEquals("Springfield", address.city());
            assertEquals("IL", address.state());
            assertEquals("62701", address.postalCode());
            assertEquals("USA", address.country());
        }
    }

    @Nested
    @DisplayName("Required fields")
    class RequiredFields {

        @Test
        @DisplayName("should reject a null street")
        void shouldRejectNullStreet() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AddressValueObject(null, "City", "ST", "00000", "Country"));
        }

        @Test
        @DisplayName("should reject a blank city")
        void shouldRejectBlankCity() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AddressValueObject("Street", "   ", "ST", "00000", "Country"));
        }

        @Test
        @DisplayName("should reject a null postal code")
        void shouldRejectNullPostalCode() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AddressValueObject("Street", "City", "ST", null, "Country"));
        }

        @Test
        @DisplayName("should reject a blank country")
        void shouldRejectBlankCountry() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AddressValueObject("Street", "City", "ST", "00000", "  "));
        }
    }

    @Nested
    @DisplayName("Length bounds (OWASP A04)")
    class LengthBounds {

        @Test
        @DisplayName("should accept a street at exactly the maximum length")
        void shouldAcceptBoundaryStreet() {
            String atLimit = "s".repeat(AddressValueObject.STREET_MAX_LENGTH);

            assertDoesNotThrow(() -> new AddressValueObject(atLimit, "City", "ST", "00000", "Country"));
        }

        @Test
        @DisplayName("should reject a street one character over the maximum")
        void shouldRejectOverlongStreet() {
            String tooLong = "s".repeat(AddressValueObject.STREET_MAX_LENGTH + 1);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> new AddressValueObject(tooLong, "City", "ST", "00000", "Country"));
            assertTrue(ex.getMessage().contains(String.valueOf(AddressValueObject.STREET_MAX_LENGTH)));
        }

        @Test
        @DisplayName("should reject an overlong optional state")
        void shouldRejectOverlongState() {
            String tooLong = "s".repeat(AddressValueObject.STATE_MAX_LENGTH + 1);

            assertThrows(IllegalArgumentException.class,
                    () -> new AddressValueObject("Street", "City", tooLong, "00000", "Country"));
        }

        @Test
        @DisplayName("should reject an overlong postal code")
        void shouldRejectOverlongPostalCode() {
            String tooLong = "1".repeat(AddressValueObject.POSTAL_CODE_MAX_LENGTH + 1);

            assertThrows(IllegalArgumentException.class,
                    () -> new AddressValueObject("Street", "City", "ST", tooLong, "Country"));
        }

        @Test
        @DisplayName("should reject an overlong country")
        void shouldRejectOverlongCountry() {
            String tooLong = "c".repeat(AddressValueObject.COUNTRY_MAX_LENGTH + 1);

            assertThrows(IllegalArgumentException.class,
                    () -> new AddressValueObject("Street", "City", "ST", "00000", tooLong));
        }
    }

    @Nested
    @DisplayName("Control characters (OWASP A03 / A09)")
    class ControlCharacters {

        @ParameterizedTest(name = "should reject a street containing [{0}]")
        @ValueSource(strings = {"123 Main St\n", "123 Main St\r\n", "123 Main St\t"})
        void shouldRejectControlCharactersInStreet(String withControlChar) {
            assertThrows(IllegalArgumentException.class,
                    () -> new AddressValueObject(withControlChar, "City", "ST", "00000", "Country"));
        }

        @Test
        @DisplayName("should reject a CRLF-injected city that could forge a log line")
        void shouldRejectLogForgingAttempt() {
            assertThrows(IllegalArgumentException.class,
                    () -> new AddressValueObject(
                            "Street", "City\r\nINFO  [AUDIT] address verified", "ST", "00000", "Country"));
        }
    }

    @Nested
    @DisplayName("Value semantics")
    class ValueSemantics {

        @Test
        @DisplayName("two addresses with identical fields should be equal and share a hash code")
        void shouldBeEqualByValue() {
            AddressValueObject a = anAddress();
            AddressValueObject b = anAddress();

            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
        }

        @Test
        @DisplayName("addresses differing only in state should not be equal")
        void shouldNotBeEqualForDifferentState() {
            AddressValueObject a = new AddressValueObject("St", "City", "CA", "00000", "USA");
            AddressValueObject b = new AddressValueObject("St", "City", "NY", "00000", "USA");

            assertNotEquals(a, b);
        }

        @Test
        @DisplayName("two addresses with the same null state should be equal")
        void shouldBeEqualWhenBothStatesAreNull() {
            AddressValueObject a = new AddressValueObject("St", "City", null, "00000", "USA");
            AddressValueObject b = new AddressValueObject("St", "City", null, "00000", "USA");

            assertEquals(a, b);
        }

        @Test
        @DisplayName("should not equal null or an unrelated type")
        void shouldNotEqualForeignTypes() {
            AddressValueObject address = anAddress();

            assertNotEquals(null, address);
            assertNotEquals("not an address", address);
        }

        @Test
        @DisplayName("toString should include every field")
        void toStringShouldIncludeAllFields() {
            String text = anAddress().toString();

            assertTrue(text.contains("Amphitheatre"));
            assertTrue(text.contains("Mountain View"));
            assertTrue(text.contains("94043"));
            assertTrue(text.contains("USA"));
        }
    }
}

package com.das.cleanddd.domain.shared;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Generic Value Object representing a postal address.
 *
 * <p>Unlike {@link StringValueObject}/{@link EmailValueObject}, an address has no
 * bounded-context-specific variation the way a file upload or a description does —
 * the same five fields and the same rules apply whether the address belongs to a
 * sales representative, a health care professional, or a visit site. It is
 * therefore a single concrete class, not an abstract base meant to be subclassed.</p>
 *
 * <p>Fields: {@code street}, {@code city}, {@code postalCode} and {@code country}
 * are required; {@code state} is optional, since not every country divides
 * addresses into states or provinces.</p>
 *
 * <p>OWASP notes:</p>
 * <ul>
 *   <li><b>A03 Injection</b> — every field rejects control characters. An address is
 *       the kind of value that ends up in logs, generated documents, and exported
 *       reports; an embedded CR/LF would allow forged log lines or injected rows in
 *       downstream text formats (also <b>A09</b>).</li>
 *   <li><b>A04 Insecure Design</b> — each field has an explicit maximum length, so an
 *       oversized value is rejected at the domain boundary rather than truncated (or
 *       rejected) deep in a persistence column.</li>
 * </ul>
 *
 * <p>Values are normalised by stripping surrounding whitespace before validation, so
 * two addresses that differ only in incidental padding are equal.</p>
 */
public final class AddressValueObject {

    public static final int STREET_MAX_LENGTH      = 255;
    public static final int CITY_MAX_LENGTH         = 100;
    public static final int STATE_MAX_LENGTH        = 100;
    public static final int POSTAL_CODE_MAX_LENGTH  = 20;
    public static final int COUNTRY_MAX_LENGTH      = 100;

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private final String street;
    private final String city;
    private final String state;
    private final String postalCode;
    private final String country;

    /**
     * @param street     required, max {@value #STREET_MAX_LENGTH} characters.
     * @param city       required, max {@value #CITY_MAX_LENGTH} characters.
     * @param state      optional (state/province); max {@value #STATE_MAX_LENGTH}
     *                   characters when present.
     * @param postalCode required, max {@value #POSTAL_CODE_MAX_LENGTH} characters.
     * @param country    required, max {@value #COUNTRY_MAX_LENGTH} characters.
     */
    public AddressValueObject(String street, String city, String state, String postalCode, String country) {
        this.street     = requireField("Street", street, STREET_MAX_LENGTH);
        this.city       = requireField("City", city, CITY_MAX_LENGTH);
        this.state      = normalizeOptional("State", state, STATE_MAX_LENGTH);
        this.postalCode = requireField("Postal code", postalCode, POSTAL_CODE_MAX_LENGTH);
        this.country    = requireField("Country", country, COUNTRY_MAX_LENGTH);
    }

    // ── Queries ────────────────────────────────────────────────────────────

    public String street()     { return street; }
    public String city()       { return city; }
    public String state()      { return state; }
    public String postalCode() { return postalCode; }
    public String country()    { return country; }

    // ── Validation ─────────────────────────────────────────────────────────

    private static String requireField(String fieldName, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return validateAndNormalize(fieldName, value, maxLength);
    }

    private static String normalizeOptional(String fieldName, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return validateAndNormalize(fieldName, value, maxLength);
    }

    /**
     * Checks for control characters on the raw, unstripped input. {@link String#strip()}
     * removes trailing whitespace including \t, \n and \r — checking only the stripped
     * value would let a control character at the very start or end of the input silently
     * disappear before the check ever ran, defeating the log-injection guard for exactly
     * the position an attacker is most likely to use.
     */
    private static String validateAndNormalize(String fieldName, String value, int maxLength) {
        if (CONTROL_CHARACTERS.matcher(value).find()) {
            throw new IllegalArgumentException(fieldName + " must not contain control characters.");
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must not exceed " + maxLength + " characters.");
        }
        return normalized;
    }

    // ── Object identity ───────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AddressValueObject other)) return false;
        return Objects.equals(street, other.street)
                && Objects.equals(city, other.city)
                && Objects.equals(state, other.state)
                && Objects.equals(postalCode, other.postalCode)
                && Objects.equals(country, other.country);
    }

    @Override
    public int hashCode() {
        return Objects.hash(street, city, state, postalCode, country);
    }

    @Override
    public String toString() {
        return "AddressValueObject{street='" + street + "', city='" + city
                + "', state='" + state + "', postalCode='" + postalCode
                + "', country='" + country + "'}";
    }
}

package com.das.cleanddd.domain.catalog.entities;

import java.util.regex.Pattern;

import com.das.cleanddd.domain.shared.StringValueObject;

/**
 * GS1 Global Trade Item Number (AI 01): identifies one trade item, i.e. one catalog product.
 *
 * <p>GTIN-8, GTIN-12 (UPC), GTIN-13 (EAN) and GTIN-14 are accepted. The GS1 mod-10 check digit must be right.
 * The value is stored right-aligned in 14 digits (zero-padded on the left), the form GS1 recommends for
 * databases, so the same item entered as GTIN-13 or GTIN-14 is recognised as one.
 */
public final class Gtin extends StringValueObject {

    private static final Pattern DIGITS = Pattern.compile("\\d{8}|\\d{12}|\\d{13}|\\d{14}");

    public Gtin(String value) {
        super(normalize(value));
    }

    private static String normalize(String value) {
        if (value == null) {
            throw new IllegalArgumentException("GTIN cannot be null.");
        }
        if (!DIGITS.matcher(value).matches()) {
            throw new IllegalArgumentException("GTIN must be 8, 12, 13 or 14 digits.");
        }
        if (value.chars().allMatch(c -> c == '0')) {
            throw new IllegalArgumentException("GTIN cannot be all zeros.");
        }
        if (!hasValidCheckDigit(value)) {
            throw new IllegalArgumentException("GTIN " + value + " has a wrong check digit.");
        }
        return "0".repeat(14 - value.length()) + value;
    }

    /** GS1 mod-10: from the right, excluding the check digit, weights alternate 3, 1, 3, … */
    static boolean hasValidCheckDigit(String digits) {
        int sum = 0;
        for (int i = digits.length() - 2, weight = 3; i >= 0; i--, weight = 4 - weight) {
            sum += (digits.charAt(i) - '0') * weight;
        }
        int check = (10 - sum % 10) % 10;
        return check == digits.charAt(digits.length() - 1) - '0';
    }
}

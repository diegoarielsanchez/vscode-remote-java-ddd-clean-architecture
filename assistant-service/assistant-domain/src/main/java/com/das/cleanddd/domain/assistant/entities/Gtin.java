package com.das.cleanddd.domain.assistant.entities;

import java.util.regex.Pattern;

import com.das.cleanddd.domain.shared.StringValueObject;

/**
 * GS1 Global Trade Item Number (AI 01), as product-catalog-service holds it for a product.
 *
 * <p>The assistant never takes a GTIN from the model: it reads it from the catalog and checks it again here, so a
 * draft line always names a trade item GS1 would accept. GTIN-8, -12, -13 and -14 are accepted and stored
 * right-aligned in 14 digits, like the catalog's own {@code Gtin}.
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

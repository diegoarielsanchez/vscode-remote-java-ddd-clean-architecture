package com.das.cleanddd.domain.catalog.entities;

import java.util.regex.Pattern;

/**
 * Format rule shared by the batch and serial numbers. Both follow the GS1 limits printed on medicine packs
 * (batch/lot is AI 10, serial is AI 21: at most 20 characters each). The character set is a strict subset of
 * GS1's: letters, digits, '-', '.' and '/'. That keeps codes unambiguous when keyed in or scanned.
 *
 * <p>Values are never trimmed or re-cased: a code is kept exactly as entered or it is refused (ALCOA+:
 * original and accurate).
 */
final class TraceabilityCode {

    static final int MAX_LENGTH = 20;
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9./-]{1," + MAX_LENGTH + "}");

    private TraceabilityCode() {}

    static String require(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " cannot be null.");
        }
        if (!ALLOWED.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be 1 to " + MAX_LENGTH
                    + " characters: letters, digits, '-', '.' or '/' (no spaces).");
        }
        return value;
    }
}

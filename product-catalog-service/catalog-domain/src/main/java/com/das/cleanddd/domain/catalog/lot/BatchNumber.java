package com.das.cleanddd.domain.catalog.lot;

import java.util.regex.Pattern;

import com.das.cleanddd.domain.shared.StringValueObject;

/**
 * GS1 batch/lot number (AI 10): at most 20 characters from GS1's character set 82 (letters, digits and
 * {@code ! " % & ' ( ) * + , - . / : ; < = > ? _}). No spaces. Kept exactly as printed: never trimmed or
 * re-cased (ALCOA+), so it matches the pack when a recall comes.
 */
public final class BatchNumber extends StringValueObject {

    public static final int MAX_LENGTH = 20;
    private static final Pattern CSET_82 = Pattern.compile("[A-Za-z0-9!\"%&'()*+,\\-./:;<=>?_]{1," + MAX_LENGTH + "}");

    public BatchNumber(String value) {
        super(check(value));
    }

    private static String check(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Batch number cannot be null.");
        }
        if (!CSET_82.matcher(value).matches()) {
            throw new IllegalArgumentException("Batch number must be 1 to " + MAX_LENGTH
                    + " characters of the GS1 character set (letters, digits and !\"%&'()*+,-./:;<=>?_; no spaces).");
        }
        return value;
    }
}

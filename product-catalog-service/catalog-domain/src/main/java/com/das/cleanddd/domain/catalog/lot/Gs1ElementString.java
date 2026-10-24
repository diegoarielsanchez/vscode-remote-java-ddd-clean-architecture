package com.das.cleanddd.domain.catalog.lot;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.das.cleanddd.domain.catalog.entities.Gtin;

/**
 * Reads the GS1 element string printed on a medicine pack (the GS1 DataMatrix), as delivered by a scanner or
 * typed from the human-readable text, e.g. {@code (01)09506000134352(17)270331(10)L2026-07A(21)SN000123}.
 *
 * <ul>
 *   <li>Two forms are accepted: the human-readable one with each AI in parentheses, and the raw scanner output,
 *       where variable-length fields end with the GS separator (ASCII 29) or at the end. A leading symbology
 *       identifier such as {@code ]d2} is ignored.</li>
 *   <li>Supported AIs: 01 GTIN (14 digits, check digit verified), 17 expiration date and 11 production date
 *       (YYMMDD; day {@code 00} means the last day of the month), 10 batch/lot and 21 serial (at most 20
 *       characters each). Any other AI, a repeated AI or malformed data is refused rather than guessed.</li>
 *   <li>The century of a YYMMDD date follows the GS1 General Specifications: it is the one that puts the year
 *       within 49 years before or 50 years after the current year.</li>
 * </ul>
 *
 * <p>In the human-readable form the parentheses delimit AIs, so a batch number containing '(' or ')' can only be
 * read from the raw form.
 */
public final class Gs1ElementString {

    public static final char GS = '\u001D';
    private static final Pattern SYMBOLOGY_IDENTIFIER = Pattern.compile("^\\][A-Za-z][0-9]");
    private static final Pattern HRI_FIELD = Pattern.compile("\\((\\d{2})\\)([^()]*)");

    private enum Ai {
        GTIN("01", 14, true), PRODUCTION_DATE("11", 6, true), EXPIRATION_DATE("17", 6, true),
        BATCH("10", 20, false), SERIAL("21", 20, false);

        final String code;
        final int length;
        final boolean fixed;

        Ai(String code, int length, boolean fixed) {
            this.code = code;
            this.length = length;
            this.fixed = fixed;
        }

        static Ai of(String code) {
            for (Ai ai : values()) {
                if (ai.code.equals(code)) return ai;
            }
            throw new IllegalArgumentException("Unsupported GS1 Application Identifier (" + code + ").");
        }
    }

    /** What the code says; each part is {@code null} when the code does not carry it. */
    public record Gs1Data(Gtin gtin, BatchNumber batchNumber, LocalDate expirationDate, LocalDate productionDate,
                          String serialNumber) {}

    private Gs1ElementString() {}

    /** @param today the current date, for the century of two-digit years */
    public static Gs1Data parse(String elementString, LocalDate today) {
        if (elementString == null || elementString.isEmpty()) {
            throw new IllegalArgumentException("GS1 code cannot be empty.");
        }
        String s = SYMBOLOGY_IDENTIFIER.matcher(elementString).replaceFirst("");
        Map<Ai, String> fields = s.startsWith("(") ? readHumanReadable(s) : readRaw(s);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("GS1 code carries no data.");
        }
        return new Gs1Data(
                fields.containsKey(Ai.GTIN) ? new Gtin(fields.get(Ai.GTIN)) : null,
                fields.containsKey(Ai.BATCH) ? new BatchNumber(fields.get(Ai.BATCH)) : null,
                fields.containsKey(Ai.EXPIRATION_DATE) ? date(fields.get(Ai.EXPIRATION_DATE), today, "17") : null,
                fields.containsKey(Ai.PRODUCTION_DATE) ? date(fields.get(Ai.PRODUCTION_DATE), today, "11") : null,
                fields.get(Ai.SERIAL));
    }

    private static Map<Ai, String> readHumanReadable(String s) {
        Map<Ai, String> fields = new LinkedHashMap<>();
        Matcher m = HRI_FIELD.matcher(s);
        int end = 0;
        while (m.find()) {
            if (m.start() != end) break;
            put(fields, Ai.of(m.group(1)), m.group(2));
            end = m.end();
        }
        if (end != s.length()) {
            throw new IllegalArgumentException("GS1 code is malformed after position " + end + ".");
        }
        return fields;
    }

    private static Map<Ai, String> readRaw(String s) {
        Map<Ai, String> fields = new LinkedHashMap<>();
        int pos = 0;
        while (pos < s.length()) {
            if (s.charAt(pos) == GS) {
                pos++;
                continue;
            }
            if (pos + 2 > s.length()) {
                throw new IllegalArgumentException("GS1 code ends in the middle of an Application Identifier.");
            }
            Ai ai = Ai.of(s.substring(pos, pos + 2));
            pos += 2;
            int end;
            if (ai.fixed) {
                end = pos + ai.length;
                if (end > s.length()) {
                    throw new IllegalArgumentException("GS1 (" + ai.code + ") must be " + ai.length + " characters.");
                }
            } else {
                int separator = s.indexOf(GS, pos);
                end = separator < 0 ? s.length() : separator;
            }
            put(fields, ai, s.substring(pos, end));
            pos = end;
        }
        return fields;
    }

    private static void put(Map<Ai, String> fields, Ai ai, String value) {
        if (fields.containsKey(ai)) {
            throw new IllegalArgumentException("GS1 (" + ai.code + ") appears more than once.");
        }
        if (ai.fixed ? !value.matches("\\d{" + ai.length + "}") : value.isEmpty() || value.length() > ai.length) {
            throw new IllegalArgumentException("GS1 (" + ai.code + ") must be "
                    + (ai.fixed ? ai.length + " digits." : "1 to " + ai.length + " characters."));
        }
        fields.put(ai, value);
    }

    /** YYMMDD with the GS1 century rule; DD = 00 is the last day of the month. */
    static LocalDate date(String yymmdd, LocalDate today, String ai) {
        int yy = Integer.parseInt(yymmdd.substring(0, 2));
        int mm = Integer.parseInt(yymmdd.substring(2, 4));
        int dd = Integer.parseInt(yymmdd.substring(4, 6));
        int currentYy = today.getYear() % 100;
        int century = today.getYear() - currentYy;
        int difference = yy - currentYy;
        if (difference >= 51) {
            century -= 100;
        } else if (difference <= -50) {
            century += 100;
        }
        try {
            YearMonth month = YearMonth.of(century + yy, mm);
            return dd == 0 ? month.atEndOfMonth() : month.atDay(dd);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("GS1 (" + ai + ") " + yymmdd + " is not a valid date.");
        }
    }
}

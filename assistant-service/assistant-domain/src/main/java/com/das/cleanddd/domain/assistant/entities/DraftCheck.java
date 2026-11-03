package com.das.cleanddd.domain.assistant.entities;

import java.util.Objects;

/**
 * One rule the draft was checked against, with its outcome. The checks are plain code ({@code DraftChecker}): the
 * model proposes lines, it never decides whether they are valid.
 *
 * @param rule      stable rule name, e.g. {@code product.active}
 * @param productId the line it is about, or {@code null} for a rule on the whole draft
 */
public record DraftCheck(String rule, String productId, CheckOutcome outcome, String message) {

    public DraftCheck {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(message, "message");
    }

    public static DraftCheck pass(String rule, String productId, String message) {
        return new DraftCheck(rule, productId, CheckOutcome.PASS, message);
    }

    public static DraftCheck warn(String rule, String productId, String message) {
        return new DraftCheck(rule, productId, CheckOutcome.WARN, message);
    }

    public static DraftCheck fail(String rule, String productId, String message) {
        return new DraftCheck(rule, productId, CheckOutcome.FAIL, message);
    }

    public boolean failed() {
        return outcome == CheckOutcome.FAIL;
    }
}

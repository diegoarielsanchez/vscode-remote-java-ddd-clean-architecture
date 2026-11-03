package com.das.cleanddd.domain.assistant.entities;

/** {@link #FAIL} blocks confirmation; {@link #WARN} is shown to the rep, who confirms knowing it. */
public enum CheckOutcome {
    PASS,
    WARN,
    FAIL
}

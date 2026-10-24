package com.das.cleanddd.domain.shared.audit;

/** One field of a record that an edit changed: its name and its value before and after, as text. */
public record FieldChange(String field, String from, String to) {}

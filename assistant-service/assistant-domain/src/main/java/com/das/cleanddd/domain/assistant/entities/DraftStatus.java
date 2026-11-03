package com.das.cleanddd.domain.assistant.entities;

/**
 * Lifecycle of an order draft. Only a person moves a draft out of {@link #DRAFTED}: the rep confirms it (an order
 * is placed) or discards it. A draft nobody decided on expires.
 */
public enum DraftStatus {
    DRAFTED,
    CONFIRMED,
    DISCARDED,
    EXPIRED
}

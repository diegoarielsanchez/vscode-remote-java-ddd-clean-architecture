package com.das.identity.domain.entities;

import java.util.UUID;

/**
 * Value object: strongly-typed User identifier.
 * Immutable — created either from an existing UUID or randomly.
 */
public final class UserId {

    private final String value;

    public UserId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("UserId must not be blank");
        }
        this.value = value;
    }

    public static UserId random() {
        return new UserId(UUID.randomUUID().toString());
    }

    public String value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserId other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}

package com.das.cleanddd.domain.shared;

import java.time.LocalDateTime;
import java.util.Objects;

public abstract class DateTimeValueObject {

    private final LocalDateTime value;

    protected DateTimeValueObject(LocalDateTime value) {
        this.value = value;
    }

    public LocalDateTime value() {
        return value;
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "null";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DateTimeValueObject)) return false;
        DateTimeValueObject that = (DateTimeValueObject) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

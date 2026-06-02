package com.das.cleanddd.domain.shared;

import java.time.LocalDate;
import java.util.Objects;

public abstract class DateValueObject {

    private final LocalDate value;

    protected DateValueObject(LocalDate value) {
        this.value = value;
    }

    public LocalDate value() {
        return value;
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "null";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DateValueObject)) return false;
        DateValueObject that = (DateValueObject) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

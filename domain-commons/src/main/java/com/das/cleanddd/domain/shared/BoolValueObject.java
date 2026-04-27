package com.das.cleanddd.domain.shared;

import java.util.Objects;

public abstract class BoolValueObject {
    protected Boolean value;

    public BoolValueObject(Boolean value) {
        this.value = value;
    }

    public Boolean value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BoolValueObject that = (BoolValueObject) o;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

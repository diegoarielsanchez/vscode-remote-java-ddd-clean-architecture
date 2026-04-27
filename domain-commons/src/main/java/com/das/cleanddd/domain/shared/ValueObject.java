package com.das.cleanddd.domain.shared;

import java.util.Objects;

public abstract class ValueObject {

   private Object value;

    public ValueObject(Object value) {
        this.value = value;
    }

    public Object value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ValueObject)) {
            return false;
        }
        ValueObject that = (ValueObject) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

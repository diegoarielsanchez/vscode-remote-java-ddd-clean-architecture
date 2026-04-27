package com.das.cleanddd.domain.shared;

import java.util.Objects;

public abstract class TextValueObject {
    private String value;

    public TextValueObject(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return this.value();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TextValueObject)) {
            return false;
        }
        TextValueObject that = (TextValueObject) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

package com.das.cleanddd.domain.shared;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Base value object for monetary/decimal amounts.
 * Equality is numeric (via {@link BigDecimal#compareTo}) so values that only
 * differ in scale (e.g. {@code 5.0} vs {@code 5.00}) are considered equal.
 */
public abstract class AmountValueObject {

    private final BigDecimal value;

    protected AmountValueObject(BigDecimal value) {
        this.value = value;
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public String toString() {
        return value != null ? value.toString() : "null";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AmountValueObject that)) return false;
        if (value == null || that.value == null) {
            return Objects.equals(value, that.value);
        }
        return value.compareTo(that.value) == 0;
    }

    @Override
    public int hashCode() {
        return value != null ? value.stripTrailingZeros().hashCode() : 0;
    }
}

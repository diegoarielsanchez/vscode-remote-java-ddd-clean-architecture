package com.das.cleanddd.domain.shared.audit;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.das.cleanddd.domain.shared.AddressValueObject;

/**
 * Builds the list of {@link FieldChange}s of an edit by comparing each field's text before and after. Values are
 * compared and recorded as text, exactly as they would be shown: {@code null} stays {@code null}, a
 * {@link BigDecimal} keeps its scale, a collection becomes its elements joined by {@code "; "} and an address its
 * parts joined by {@code ", "}.
 */
public final class FieldChanges {

    private final List<FieldChange> changes = new ArrayList<>();

    public FieldChanges compare(String field, Object before, Object after) {
        String from = text(before);
        String to = text(after);
        if (!Objects.equals(from, to)) {
            changes.add(new FieldChange(field, from, to));
        }
        return this;
    }

    public List<FieldChange> toList() {
        return List.copyOf(changes);
    }

    static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof AddressValueObject address) {
            return Stream.of(address.street(), address.city(), address.state(), address.postalCode(),
                            address.country())
                    .filter(Objects::nonNull).collect(Collectors.joining(", "));
        }
        if (value instanceof Collection<?> items) {
            return items.stream().map(FieldChanges::text).collect(Collectors.joining("; "));
        }
        return value.toString();
    }
}

package com.das.cleanddd.domain.shared.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.das.cleanddd.domain.shared.AddressValueObject;

class FieldChangesTest {

    @Test
    void recordsOnlyTheFieldsThatChangedWithTheirPreviousValue() {
        List<FieldChange> changes = new FieldChanges()
                .compare("name", "Ana", "Ana")
                .compare("email", "ana@old.example", "ana@new.example")
                .compare("description", "Antibiotic", null)
                .toList();

        assertEquals(List.of(new FieldChange("email", "ana@old.example", "ana@new.example"),
                new FieldChange("description", "Antibiotic", null)), changes);
    }

    @Test
    void comparesValuesAsTheyAreShown() {
        AddressValueObject before = new AddressValueObject("1600 Amphitheatre Pkwy", "Mountain View", "CA", "94043", "USA");
        AddressValueObject after = new AddressValueObject("1 Infinite Loop", "Cupertino", "CA", "95014", "USA");

        List<FieldChange> changes = new FieldChanges()
                .compare("price", new BigDecimal("12.50"), new BigDecimal("12.50"))
                .compare("unitPrice", new BigDecimal("12.50"), new BigDecimal("12.5"))
                .compare("addresses", List.of(before), List.of(before, after))
                .toList();

        assertEquals(new FieldChange("unitPrice", "12.50", "12.5"), changes.get(0), "the stored scale changed");
        assertEquals(new FieldChange("addresses", "1600 Amphitheatre Pkwy, Mountain View, CA, 94043, USA",
                "1600 Amphitheatre Pkwy, Mountain View, CA, 94043, USA; 1 Infinite Loop, Cupertino, CA, 95014, USA"),
                changes.get(1));
        assertEquals(2, changes.size());
    }

    @Test
    void theListCannotBeAlteredAfterwards() {
        List<FieldChange> changes = new FieldChanges().compare("name", "a", "b").toList();
        assertThrows(UnsupportedOperationException.class, () -> changes.add(new FieldChange("x", null, null)));
    }
}

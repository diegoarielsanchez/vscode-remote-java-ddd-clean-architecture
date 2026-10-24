package com.das.cleanddd.domain.shared.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ChangeReasonTest {

    @Test
    void keepsTheReasonAsEnteredWithoutSurroundingBlanks() {
        assertEquals("Typo in surname, per HCP's e-mail of 24 Oct", new ChangeReason("  Typo in surname, per HCP's e-mail of 24 Oct ").value());
    }

    @Test
    void isRequired() {
        assertThrows(IllegalArgumentException.class, () -> new ChangeReason(null));
        assertThrows(IllegalArgumentException.class, () -> new ChangeReason("   "));
    }

    @Test
    void isAtMost500Characters() {
        assertEquals(500, new ChangeReason("x".repeat(500)).value().length());
        assertThrows(IllegalArgumentException.class, () -> new ChangeReason("x".repeat(501)));
    }

    @Test
    void valueOfAMissingReasonIsNull() {
        assertNull(ChangeReason.valueOf(null));
    }
}

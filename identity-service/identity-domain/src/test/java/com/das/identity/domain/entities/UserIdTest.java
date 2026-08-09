package com.das.identity.domain.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserId Value Object")
class UserIdTest {

    // ── construction guards ──────────────────────────────────────────────────

    @Test
    @DisplayName("should reject a null value")
    void shouldRejectNull() {
        assertThrows(IllegalArgumentException.class, () -> new UserId(null));
    }

    @Test
    @DisplayName("should reject a blank value")
    void shouldRejectBlank() {
        assertThrows(IllegalArgumentException.class, () -> new UserId("   "));
    }

    @Test
    @DisplayName("should expose the value it was built with")
    void shouldExposeValue() {
        assertEquals("abc-123", new UserId("abc-123").value());
    }

    // ── random() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("random() should produce a distinct id on each call")
    void randomShouldBeUnique() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            generated.add(UserId.random().value());
        }
        assertEquals(100, generated.size());
    }

    // ── value semantics ──────────────────────────────────────────────────────

    @Test
    @DisplayName("two ids with the same value should be equal and share a hash code")
    void shouldBeEqualByValue() {
        UserId a = new UserId("same");
        UserId b = new UserId("same");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("ids with different values should not be equal")
    void shouldNotBeEqualForDifferentValues() {
        assertNotEquals(new UserId("one"), new UserId("two"));
    }

    @Test
    @DisplayName("should not be equal to null or to an unrelated type")
    void shouldNotEqualForeignTypes() {
        UserId id = new UserId("abc");

        assertNotEquals(null, id);
        assertNotEquals("abc", id);
    }

    @Test
    @DisplayName("toString should return the raw value")
    void toStringShouldReturnValue() {
        assertEquals("abc-123", new UserId("abc-123").toString());
    }
}

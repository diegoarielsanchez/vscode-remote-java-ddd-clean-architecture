package com.das.identity.domain.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("User Aggregate Root")
class UserTest {

    private static final String VALID_HASH = "$2a$12$abcdefghijklmnopqrstuv";

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create an active user with the given username, hash and roles")
        void shouldCreateActiveUser() {
            User user = User.create("alice", VALID_HASH, List.of(UserRole.ROLE_USER));

            assertEquals("alice", user.getUsername());
            assertEquals(VALID_HASH, user.getPasswordHash());
            assertEquals(List.of(UserRole.ROLE_USER), user.getRoles());
            assertTrue(user.isActive(), "a newly created user must be active");
            assertNotNull(user.getId());
        }

        @Test
        @DisplayName("should assign a distinct id to each created user")
        void shouldAssignDistinctIds() {
            User a = User.create("alice", VALID_HASH, List.of(UserRole.ROLE_USER));
            User b = User.create("bob",   VALID_HASH, List.of(UserRole.ROLE_USER));

            assertNotEquals(a.getId(), b.getId());
        }

        @Test
        @DisplayName("should accept a username of exactly 64 characters")
        void shouldAcceptBoundaryUsername() {
            String username = "u".repeat(64);

            assertDoesNotThrow(() ->
                    User.create(username, VALID_HASH, List.of(UserRole.ROLE_USER)));
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should reject a null username")
        void shouldRejectNullUsername() {
            assertThrows(IllegalArgumentException.class,
                    () -> User.create(null, VALID_HASH, List.of(UserRole.ROLE_USER)));
        }

        @Test
        @DisplayName("should reject a blank username")
        void shouldRejectBlankUsername() {
            assertThrows(IllegalArgumentException.class,
                    () -> User.create("  ", VALID_HASH, List.of(UserRole.ROLE_USER)));
        }

        @Test
        @DisplayName("should reject a username longer than 64 characters")
        void shouldRejectOverlongUsername() {
            String username = "u".repeat(65);

            assertThrows(IllegalArgumentException.class,
                    () -> User.create(username, VALID_HASH, List.of(UserRole.ROLE_USER)));
        }

        @Test
        @DisplayName("should reject a null password hash")
        void shouldRejectNullHash() {
            assertThrows(IllegalArgumentException.class,
                    () -> User.create("alice", null, List.of(UserRole.ROLE_USER)));
        }

        @Test
        @DisplayName("should reject a blank password hash")
        void shouldRejectBlankHash() {
            assertThrows(IllegalArgumentException.class,
                    () -> User.create("alice", "   ", List.of(UserRole.ROLE_USER)));
        }

        @Test
        @DisplayName("should reject a null role list")
        void shouldRejectNullRoles() {
            assertThrows(IllegalArgumentException.class,
                    () -> User.create("alice", VALID_HASH, null));
        }

        @Test
        @DisplayName("should reject an empty role list — every user needs at least one role")
        void shouldRejectEmptyRoles() {
            assertThrows(IllegalArgumentException.class,
                    () -> User.create("alice", VALID_HASH, List.of()));
        }
    }

    @Nested
    @DisplayName("Reconstitution")
    class Reconstitution {

        @Test
        @DisplayName("should rebuild a user preserving id, username, hash, roles and active flag")
        void shouldPreserveStoredState() {
            UserId id = new UserId("stored-id");

            User user = User.reconstitute(id, "bob", VALID_HASH,
                    List.of(UserRole.ROLE_ADMIN), false);

            assertEquals(id, user.getId());
            assertEquals("bob", user.getUsername());
            assertEquals(VALID_HASH, user.getPasswordHash());
            assertEquals(List.of(UserRole.ROLE_ADMIN), user.getRoles());
            assertFalse(user.isActive(), "the stored inactive flag must survive reconstitution");
        }

        @Test
        @DisplayName("should support multiple roles")
        void shouldSupportMultipleRoles() {
            User user = User.reconstitute(UserId.random(), "root", VALID_HASH,
                    List.of(UserRole.ROLE_USER, UserRole.ROLE_ADMIN), true);

            assertEquals(2, user.getRoles().size());
            assertTrue(user.getRoles().contains(UserRole.ROLE_ADMIN));
        }
    }

    @Nested
    @DisplayName("Immutability")
    class Immutability {

        @Test
        @DisplayName("getRoles() should return a list that cannot be modified by callers")
        void rolesShouldBeUnmodifiable() {
            User user = User.create("alice", VALID_HASH, new ArrayList<>(List.of(UserRole.ROLE_USER)));

            assertThrows(UnsupportedOperationException.class,
                    () -> user.getRoles().add(UserRole.ROLE_ADMIN));
        }

        @Test
        @DisplayName("mutating the source role list after creation should not escalate a user's roles")
        void shouldNotLeakSourceListMutations() {
            List<UserRole> source = new ArrayList<>(List.of(UserRole.ROLE_USER));
            User user = User.create("alice", VALID_HASH, source);

            source.add(UserRole.ROLE_ADMIN);

            assertFalse(user.getRoles().contains(UserRole.ROLE_ADMIN),
                    "a user's roles must not change because the caller kept a reference to the list");
        }
    }
}

package com.das.identity.infra;

import com.das.identity.domain.entities.User;
import com.das.identity.domain.entities.UserId;
import com.das.identity.domain.entities.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryUserRepository")
class InMemoryUserRepositoryTest {

    /** BCrypt (cost 12) of the dev password — the same default application.properties ships. */
    private static final String SEED_HASH = "$2a$12$oxidnedWihAzJbsAXyZH5OX3caBTkilYZ5Zn0pqpUt.Fv/CiYUH26";

    private InMemoryUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository("user", SEED_HASH, "", "");
    }

    // ── seeding ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should seed the configured user on construction")
    void shouldSeedConfiguredUser() {
        Optional<User> user = repository.findByUsername("user");

        assertTrue(user.isPresent());
        assertEquals("user", user.get().getUsername());
    }

    @Test
    @DisplayName("the seeded user should be active and hold ROLE_USER")
    void seededUserShouldBeActiveWithUserRole() {
        User user = repository.findByUsername("user").orElseThrow();

        assertTrue(user.isActive());
        assertEquals(List.of(UserRole.ROLE_USER), user.getRoles());
    }

    @Test
    @DisplayName("should store the configured hash as-is, so it verifies with real BCrypt")
    void shouldStoreConfiguredHash() {
        User user = repository.findByUsername("user").orElseThrow();

        assertEquals(SEED_HASH, user.getPasswordHash());
        assertTrue(new BCryptPasswordEncoderAdapter().matches("Apatehia65$", user.getPasswordHash()));
    }

    @Test
    @DisplayName("should seed nobody when no username is configured")
    void shouldSeedNobodyWithoutUsername() {
        InMemoryUserRepository empty = new InMemoryUserRepository("", "", "", "");

        assertTrue(empty.findByUsername("user").isEmpty());
    }

    @Test
    @DisplayName("should refuse to start with a plain-text password instead of a BCrypt hash")
    void shouldRejectPlainTextPassword() {
        assertThrows(IllegalStateException.class,
                () -> new InMemoryUserRepository("user", "Apatehia65$", "", ""));
    }

    @Test
    @DisplayName("should seed the agent account with ROLE_AGENT when configured")
    void shouldSeedAgentWithAgentRole() {
        InMemoryUserRepository repo = new InMemoryUserRepository("user", SEED_HASH, "agent", SEED_HASH);

        assertEquals(List.of(UserRole.ROLE_USER, UserRole.ROLE_AGENT),
                repo.findByUsername("agent").orElseThrow().getRoles());
        assertEquals(List.of(UserRole.ROLE_USER), repo.findByUsername("user").orElseThrow().getRoles(),
                "the regular user must never receive ROLE_AGENT");
    }

    @Test
    @DisplayName("should seed no agent by default, so nobody can approve orders or change stock")
    void shouldSeedNoAgentByDefault() {
        assertTrue(repository.findByUsername("agent").isEmpty());
    }

    @Test
    @DisplayName("should refuse to start with a plain-text agent password")
    void shouldRejectPlainTextAgentPassword() {
        assertThrows(IllegalStateException.class,
                () -> new InMemoryUserRepository("user", SEED_HASH, "agent", "secret"));
    }

    // ── lookup ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should return empty for an unknown username")
    void shouldReturnEmptyForUnknownUser() {
        assertTrue(repository.findByUsername("ghost").isEmpty());
    }

    @Test
    @DisplayName("lookup should be case-sensitive")
    void lookupShouldBeCaseSensitive() {
        assertTrue(repository.findByUsername("USER").isEmpty());
    }

    // ── save ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should store and retrieve a saved user")
    void shouldRoundTripSavedUser() {
        User alice = User.create("alice", "hashed:secret", List.of(UserRole.ROLE_ADMIN));

        repository.save(alice);

        User found = repository.findByUsername("alice").orElseThrow();
        assertEquals(alice.getId(), found.getId());
        assertEquals(List.of(UserRole.ROLE_ADMIN), found.getRoles());
    }

    @Test
    @DisplayName("saving the same username twice should overwrite the previous record")
    void shouldOverwriteOnDuplicateUsername() {
        repository.save(User.reconstitute(new UserId("id-1"), "alice", "hashed:one",
                List.of(UserRole.ROLE_USER), true));
        repository.save(User.reconstitute(new UserId("id-2"), "alice", "hashed:two",
                List.of(UserRole.ROLE_ADMIN), true));

        User found = repository.findByUsername("alice").orElseThrow();
        assertEquals("id-2", found.getId().value());
        assertEquals(List.of(UserRole.ROLE_ADMIN), found.getRoles());
    }

    @Test
    @DisplayName("should keep the seeded user alongside newly saved users")
    void shouldNotEvictSeededUser() {
        repository.save(User.create("alice", "hashed:secret", List.of(UserRole.ROLE_USER)));

        assertTrue(repository.findByUsername("user").isPresent());
        assertTrue(repository.findByUsername("alice").isPresent());
    }
}

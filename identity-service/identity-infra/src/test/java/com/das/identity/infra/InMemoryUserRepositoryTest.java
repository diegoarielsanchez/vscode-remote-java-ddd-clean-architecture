package com.das.identity.infra;

import com.das.identity.domain.entities.User;
import com.das.identity.domain.entities.UserId;
import com.das.identity.domain.entities.UserRole;
import com.das.identity.domain.ports.PasswordEncoderPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryUserRepository")
class InMemoryUserRepositoryTest {

    /** Cheap stand-in so the structural tests do not pay the BCrypt cost-12 price. */
    private static final class FakeEncoder implements PasswordEncoderPort {
        @Override public String encode(CharSequence rawPassword) {
            return "hashed:" + rawPassword;
        }
        @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
            return encodedPassword.equals("hashed:" + rawPassword);
        }
    }

    private InMemoryUserRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryUserRepository(new FakeEncoder());
    }

    // ── seeding ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("should seed the default dev user on construction")
    void shouldSeedDevUser() {
        Optional<User> user = repository.findByUsername("user");

        assertTrue(user.isPresent());
        assertEquals("user", user.get().getUsername());
    }

    @Test
    @DisplayName("the seeded dev user should be active and hold ROLE_USER")
    void seededUserShouldBeActiveWithUserRole() {
        User user = repository.findByUsername("user").orElseThrow();

        assertTrue(user.isActive());
        assertEquals(List.of(UserRole.ROLE_USER), user.getRoles());
    }

    @Test
    @DisplayName("should route the seeded password through the injected encoder, not store it raw")
    void shouldSeedHashedPassword() {
        User user = repository.findByUsername("user").orElseThrow();

        assertNotEquals("Apatehia65$", user.getPasswordHash());
        assertEquals(new FakeEncoder().encode("Apatehia65$"), user.getPasswordHash(),
                "the seed must be encoded by the injected port, never assigned directly");
    }

    @Test
    @DisplayName("should hash the seeded password with real BCrypt, leaving no recoverable plain text")
    void shouldSeedWithInjectedEncoder() {
        BCryptPasswordEncoderAdapter realEncoder = new BCryptPasswordEncoderAdapter();
        InMemoryUserRepository repo = new InMemoryUserRepository(realEncoder);

        User user = repo.findByUsername("user").orElseThrow();

        assertTrue(user.getPasswordHash().startsWith("$2a$12$"));
        assertTrue(realEncoder.matches("Apatehia65$", user.getPasswordHash()));
        assertFalse(user.getPasswordHash().contains("Apatehia65$".substring(0, 8)),
                "the raw password must not be recoverable from the stored hash");
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

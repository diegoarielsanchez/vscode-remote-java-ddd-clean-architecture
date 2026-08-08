package com.das.identity.domain.entities;

import java.util.List;

/**
 * Aggregate root: represents an identity principal (user account).
 * <p>
 * OWASP notes:
 * - Password is always stored as a hash — never plain-text.
 * - active flag allows soft-disabling without deleting the record.
 * - Immutable after construction; use factory method for creation.
 */
public final class User {

    private final UserId id;
    private final String username;
    private final String passwordHash;
    private final List<UserRole> roles;
    private final boolean active;

    private User(UserId id, String username, String passwordHash,
                 List<UserRole> roles, boolean active) {
        this.id           = id;
        this.username     = username;
        this.passwordHash = passwordHash;
        // Defensive copy, not a wrapper view: unmodifiableList would still reflect later
        // mutations of the caller's list, letting a retained reference escalate roles (A01).
        this.roles        = List.copyOf(roles);
        this.active       = active;
    }

    /**
     * Factory: creates a new active User.
     *
     * @param username     login name (must be unique in the repository)
     * @param passwordHash BCrypt hash of the raw password
     * @param roles        list of granted roles
     */
    public static User create(String username, String passwordHash, List<UserRole> roles) {
        validateUsername(username);
        validatePasswordHash(passwordHash);
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("At least one role must be assigned");
        }
        return new User(UserId.random(), username, passwordHash, roles, true);
    }

    /**
     * Reconstitution factory: rebuilds a User from persistent storage.
     */
    public static User reconstitute(UserId id, String username, String passwordHash,
                                    List<UserRole> roles, boolean active) {
        return new User(id, username, passwordHash, roles, active);
    }

    // ── Accessors ───────────────────────────────────────────────────────────

    public UserId getId()           { return id; }
    public String getUsername()     { return username; }
    public String getPasswordHash() { return passwordHash; }
    public List<UserRole> getRoles(){ return roles; }
    public boolean isActive()       { return active; }

    // ── Private guards ───────────────────────────────────────────────────────

    private static void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        if (username.length() > 64) {
            throw new IllegalArgumentException("Username must not exceed 64 characters");
        }
    }

    private static void validatePasswordHash(String hash) {
        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("Password hash must not be blank");
        }
    }
}

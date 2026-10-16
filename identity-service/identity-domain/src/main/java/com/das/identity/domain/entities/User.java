package com.das.identity.domain.entities;

import java.util.List;
import java.util.Optional;

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
    /** The MSR record this account acts as, if any; carried in the token as {@code msr_id}. */
    private final String medicalSalesRepId;

    private User(UserId id, String username, String passwordHash,
                 List<UserRole> roles, boolean active, String medicalSalesRepId) {
        this.id           = id;
        this.username     = username;
        this.passwordHash = passwordHash;
        // Defensive copy, not a wrapper view: unmodifiableList would still reflect later
        // mutations of the caller's list, letting a retained reference escalate roles (A01).
        this.roles        = List.copyOf(roles);
        this.active       = active;
        this.medicalSalesRepId = (medicalSalesRepId == null || medicalSalesRepId.isBlank()) ? null : medicalSalesRepId;
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
        return new User(UserId.random(), username, passwordHash, roles, true, null);
    }

    /**
     * Reconstitution factory: rebuilds a User from persistent storage.
     */
    public static User reconstitute(UserId id, String username, String passwordHash,
                                    List<UserRole> roles, boolean active) {
        return reconstitute(id, username, passwordHash, roles, active, null);
    }

    /**
     * Reconstitution factory for an account linked to a Medical Sales Rep.
     */
    public static User reconstitute(UserId id, String username, String passwordHash,
                                    List<UserRole> roles, boolean active, String medicalSalesRepId) {
        return new User(id, username, passwordHash, roles, active, medicalSalesRepId);
    }

    // ── Accessors ───────────────────────────────────────────────────────────

    public UserId getId()           { return id; }
    public String getUsername()     { return username; }
    public String getPasswordHash() { return passwordHash; }
    public List<UserRole> getRoles(){ return roles; }
    public boolean isActive()       { return active; }
    public Optional<String> getMedicalSalesRepId() { return Optional.ofNullable(medicalSalesRepId); }

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

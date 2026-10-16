package com.das.identity.domain.entities;

/**
 * Value object: role assigned to a User.
 * Drives authorization claims embedded in the JWT.
 */
public enum UserRole {
    ROLE_USER,
    ROLE_ADMIN,
    /** The AI Agent Service: the only caller allowed to approve orders and change stock. */
    ROLE_AGENT
}

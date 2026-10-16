package com.das.identity.domain.entities;

/**
 * Value object: role assigned to a User.
 * Drives authorization claims embedded in the JWT.
 */
public enum UserRole {
    ROLE_USER,
    /** Manages the catalog and activates/deactivates HCPs, MSRs and products. */
    ROLE_ADMIN,
    /** The AI Agent Service: the only caller allowed to approve orders and change stock. */
    ROLE_AGENT
}

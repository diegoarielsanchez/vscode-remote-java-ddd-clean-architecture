package com.das.identity.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Accounts {@link InMemoryUserRepository} seeds at startup, bound from {@code identity.*}.
 * Any account left without a username is simply not created.
 *
 * @param seedUser  regular user (ROLE_USER)
 * @param agentUser the AI Agent Service (ROLE_USER + ROLE_AGENT): approves orders, changes stock
 * @param adminUser administrator (ROLE_USER + ROLE_ADMIN): manages the catalog, activates records
 */
@ConfigurationProperties(prefix = "identity")
public record SeedAccountsProperties(Account seedUser, Account agentUser, Account adminUser) {

    /**
     * @param passwordHash      BCrypt hash — never a plain-text password
     * @param medicalSalesRepId optional MSR this account acts as (emitted as the {@code msr_id} claim)
     */
    public record Account(String username, String passwordHash, String medicalSalesRepId) {

        boolean isConfigured() {
            return username != null && !username.isBlank();
        }
    }
}

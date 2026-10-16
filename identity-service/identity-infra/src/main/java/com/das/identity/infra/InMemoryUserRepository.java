package com.das.identity.infra;

import com.das.identity.domain.entities.User;
import com.das.identity.domain.entities.UserId;
import com.das.identity.domain.entities.UserRole;
import com.das.identity.domain.ports.UserRepository;
import com.das.identity.infra.SeedAccountsProperties.Account;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * In-memory UserRepository.
 * <p>
 * Replace this with a JPA/DB-backed implementation without changing the domain.
 * Seeds the accounts in {@link SeedAccountsProperties}: the regular user, and optionally the
 * AI Agent Service and an administrator. Passwords arrive already BCrypt-hashed, so no
 * plain-text credential lives in source or config (OWASP A07). The dev profile supplies a
 * default regular user; the prod profile requires it from the environment. The agent and
 * admin exist only when configured, in every profile.
 */
@Service
@EnableConfigurationProperties(SeedAccountsProperties.class)
public class InMemoryUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryUserRepository.class);

    /** Same shape Spring's BCryptPasswordEncoder accepts; anything else could never match. */
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[aby]?\\$\\d\\d\\$[./0-9A-Za-z]{53}");

    private final Map<String, User> store = new ConcurrentHashMap<>();

    public InMemoryUserRepository(SeedAccountsProperties accounts) {
        seed("identity.seed-user", accounts.seedUser(), List.of(UserRole.ROLE_USER),
                "no user can log in");
        // ROLE_USER too: the agent and admin read records before acting on them.
        seed("identity.agent-user", accounts.agentUser(), List.of(UserRole.ROLE_USER, UserRole.ROLE_AGENT),
                "no one can approve orders or change stock");
        seed("identity.admin-user", accounts.adminUser(), List.of(UserRole.ROLE_USER, UserRole.ROLE_ADMIN),
                "no one can manage the catalog or activate records");
    }

    private void seed(String property, Account account, List<UserRole> roles, String consequenceIfMissing) {
        if (account == null || !account.isConfigured()) {
            log.info("No {}.username configured — {}", property, consequenceIfMissing);
            return;
        }
        if (account.passwordHash() == null || !BCRYPT_HASH.matcher(account.passwordHash()).matches()) {
            // Fail fast: a plain-text password here would otherwise just never authenticate.
            throw new IllegalStateException(
                    property + ".password-hash must be a BCrypt hash, not a plain-text password");
        }
        store.put(account.username(), User.reconstitute(UserId.random(), account.username(),
                account.passwordHash(), roles, true, account.medicalSalesRepId()));
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(store.get(username));
    }

    @Override
    public void save(User user) {
        store.put(user.getUsername(), user);
    }
}

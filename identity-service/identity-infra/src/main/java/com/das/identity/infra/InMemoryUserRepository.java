package com.das.identity.infra;

import com.das.identity.domain.entities.User;
import com.das.identity.domain.entities.UserId;
import com.das.identity.domain.entities.UserRole;
import com.das.identity.domain.ports.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * Seeds one user from {@code identity.seed-user.*} and, optionally, the AI Agent Service
 * account from {@code identity.agent-user.*}. Passwords arrive already BCrypt-hashed, so no
 * plain-text credential lives in source or config (OWASP A07). The dev profile supplies a
 * default user; the prod profile requires it from the environment.
 */
@Service
public class InMemoryUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryUserRepository.class);

    /** Same shape Spring's BCryptPasswordEncoder accepts; anything else could never match. */
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[aby]?\\$\\d\\d\\$[./0-9A-Za-z]{53}");

    private final Map<String, User> store = new ConcurrentHashMap<>();

    public InMemoryUserRepository(
            @Value("${identity.seed-user.username:}") String seedUsername,
            @Value("${identity.seed-user.password-hash:}") String seedPasswordHash,
            @Value("${identity.agent-user.username:}") String agentUsername,
            @Value("${identity.agent-user.password-hash:}") String agentPasswordHash) {
        if (seedUsername.isBlank()) {
            log.warn("No identity.seed-user.username configured — no user can log in");
        } else {
            seed("identity.seed-user", seedUsername, seedPasswordHash, List.of(UserRole.ROLE_USER));
        }
        if (agentUsername.isBlank()) {
            log.info("No identity.agent-user.username configured — no one can approve orders or change stock");
        } else {
            // ROLE_USER too: the agent reads orders and products before deciding.
            seed("identity.agent-user", agentUsername, agentPasswordHash,
                    List.of(UserRole.ROLE_USER, UserRole.ROLE_AGENT));
        }
    }

    private void seed(String property, String username, String passwordHash, List<UserRole> roles) {
        if (!BCRYPT_HASH.matcher(passwordHash).matches()) {
            // Fail fast: a plain-text password here would otherwise just never authenticate.
            throw new IllegalStateException(
                    property + ".password-hash must be a BCrypt hash, not a plain-text password");
        }
        store.put(username, User.reconstitute(UserId.random(), username, passwordHash, roles, true));
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

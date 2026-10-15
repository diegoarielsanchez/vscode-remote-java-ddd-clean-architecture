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
 * Seeds one user from {@code identity.seed-user.*}. The password arrives already
 * BCrypt-hashed, so no plain-text credential lives in source or config (OWASP A07).
 * The dev profile supplies a default; the prod profile requires it from the environment.
 */
@Service
public class InMemoryUserRepository implements UserRepository {

    private static final Logger log = LoggerFactory.getLogger(InMemoryUserRepository.class);

    /** Same shape Spring's BCryptPasswordEncoder accepts; anything else could never match. */
    private static final Pattern BCRYPT_HASH = Pattern.compile("\\$2[aby]?\\$\\d\\d\\$[./0-9A-Za-z]{53}");

    private final Map<String, User> store = new ConcurrentHashMap<>();

    public InMemoryUserRepository(
            @Value("${identity.seed-user.username:}") String seedUsername,
            @Value("${identity.seed-user.password-hash:}") String seedPasswordHash) {
        if (seedUsername.isBlank()) {
            log.warn("No identity.seed-user.username configured — no user can log in");
            return;
        }
        if (!BCRYPT_HASH.matcher(seedPasswordHash).matches()) {
            // Fail fast: a plain-text password here would otherwise just never authenticate.
            throw new IllegalStateException(
                    "identity.seed-user.password-hash must be a BCrypt hash, not a plain-text password");
        }
        User seedUser = User.reconstitute(
            UserId.random(),
            seedUsername,
            seedPasswordHash,
            List.of(UserRole.ROLE_USER),
            true
        );
        store.put(seedUser.getUsername(), seedUser);
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

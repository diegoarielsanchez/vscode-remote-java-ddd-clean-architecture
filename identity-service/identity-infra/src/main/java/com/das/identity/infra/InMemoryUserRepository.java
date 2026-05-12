package com.das.identity.infra;

import com.das.identity.domain.entities.User;
import com.das.identity.domain.entities.UserRole;
import com.das.identity.domain.ports.PasswordEncoderPort;
import com.das.identity.domain.ports.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory UserRepository for local development.
 * <p>
 * Replace this with a JPA/DB-backed implementation without changing the domain.
 * Seeds a default dev user on startup; production deployments must override via env-vars.
 */
@Service
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> store = new ConcurrentHashMap<>();

    /**
     * Seeds the default dev user.
     * Password is BCrypt-encoded at construction time using the injected encoder.
     * The plain-text password ("Apatehia65$") NEVER appears at runtime.
     */
    public InMemoryUserRepository(PasswordEncoderPort passwordEncoder) {
        String hash = passwordEncoder.encode("Apatehia65$");
        User devUser = User.reconstitute(
            com.das.identity.domain.entities.UserId.random(),
            "user",
            hash,
            List.of(UserRole.ROLE_USER),
            true
        );
        store.put(devUser.getUsername(), devUser);
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

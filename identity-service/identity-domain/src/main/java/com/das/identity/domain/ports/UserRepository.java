package com.das.identity.domain.ports;

import com.das.identity.domain.entities.User;

import java.util.Optional;

/**
 * Repository port (driven port): abstracts User persistence.
 * Implementations live in identity-infra.
 */
public interface UserRepository {

    Optional<User> findByUsername(String username);

    void save(User user);
}

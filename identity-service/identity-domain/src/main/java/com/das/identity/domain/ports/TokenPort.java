package com.das.identity.domain.ports;

import com.das.identity.domain.entities.User;

/**
 * Port: JWT token generation.
 * Keeps JWT library dependencies out of the domain layer.
 */
public interface TokenPort {

    /**
     * Generates a signed JWT for the given user.
     *
     * @param user      the authenticated principal
     * @return signed JWT string
     */
    String generateToken(User user);
}

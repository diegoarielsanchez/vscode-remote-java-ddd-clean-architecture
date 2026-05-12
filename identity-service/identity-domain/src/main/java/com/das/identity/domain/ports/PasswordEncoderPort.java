package com.das.identity.domain.ports;

/**
 * Port: password hashing and verification.
 * Keeps the domain free of BCrypt/framework dependencies.
 */
public interface PasswordEncoderPort {

    /** Encodes a raw plain-text password. */
    String encode(CharSequence rawPassword);

    /** Returns true if rawPassword matches the stored encodedPassword. */
    boolean matches(CharSequence rawPassword, String encodedPassword);
}

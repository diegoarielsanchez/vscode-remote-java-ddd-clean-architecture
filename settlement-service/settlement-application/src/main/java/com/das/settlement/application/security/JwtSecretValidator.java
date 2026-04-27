package com.das.settlement.application.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class JwtSecretValidator {

    private static final int MIN_SECRET_LENGTH = 32;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @PostConstruct
    public void validate() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("jwt.secret must be configured and non-empty");
        }
        if (jwtSecret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("jwt.secret must be at least 32 characters long");
        }
        String normalized = jwtSecret.toLowerCase();
        if (normalized.contains("change-this") || normalized.contains("your-secret-key")) {
            throw new IllegalStateException("jwt.secret uses an insecure placeholder value");
        }
    }
}

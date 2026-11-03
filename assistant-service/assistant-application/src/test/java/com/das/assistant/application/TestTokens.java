package com.das.assistant.application;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Jwts;

/** Signs tokens exactly like identity-service, so the real JwtAuthenticationFilter is exercised. */
final class TestTokens {

    static final String JWT_SECRET = "test-secret-key-for-integration-tests-only-32chars";

    private TestTokens() {
    }

    /** A token with the msr_id claim identity-service adds for an account linked to an MSR, or none if null. */
    static String bearerForRep(String msrId, String... authorities) {
        byte[] key = JWT_SECRET.getBytes(StandardCharsets.UTF_8);
        var builder = Jwts.builder()
                .subject("tester")
                .claim("authorities", List.of(authorities));
        if (msrId != null) {
            builder.claim("msr_id", msrId);
        }
        return "Bearer " + builder
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(new SecretKeySpec(key, "HmacSHA256"), Jwts.SIG.HS256)
                .compact();
    }
}

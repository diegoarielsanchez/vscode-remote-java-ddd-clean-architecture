package com.das.identity.infra;

import com.das.identity.domain.entities.User;
import com.das.identity.domain.ports.TokenPort;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * Adapter: JWT token generation using jjwt.
 * <p>
 * OWASP notes:
 * - A02: signs tokens with HS256 using a secret injected from env-var (never hard-coded in prod).
 * - Token carries only non-sensitive claims (username, roles) — no passwords or PII.
 * - Short-lived tokens (default 1 h) reduce blast radius of token theft.
 */
@Service
public class JwtTokenAdapter implements TokenPort {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationMs;

    @Override
    public String generateToken(User user) {
        SecretKey key = buildKey();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("authorities", user.getRoles().stream()
                        .map(role -> role.name())
                        .collect(Collectors.toList()))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    private SecretKey buildKey() {
        byte[] bytes = jwtSecret.getBytes();
        return new SecretKeySpec(bytes, 0, bytes.length, "HmacSHA256");
    }
}

package com.das.identity.infra;

import com.das.identity.domain.entities.User;
import com.das.identity.domain.entities.UserId;
import com.das.identity.domain.entities.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies JWT issuance (OWASP A02 — Cryptographic Failures, A07 — Auth Failures).
 * <p>
 * {@code jwtSecret} and {@code jwtExpirationMs} are normally injected by Spring from
 * {@code @Value}; here they are set directly so the adapter can be tested without a context.
 */
@DisplayName("JwtTokenAdapter")
class JwtTokenAdapterTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes-of-entropy";
    private static final long   ONE_HOUR_MS = 3_600_000L;

    private JwtTokenAdapter adapter;
    private User user;

    private static SecretKey key(String secret) {
        byte[] bytes = secret.getBytes();
        return new SecretKeySpec(bytes, 0, bytes.length, "HmacSHA256");
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key(SECRET))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @BeforeEach
    void setUp() {
        adapter = new JwtTokenAdapter();
        ReflectionTestUtils.setField(adapter, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(adapter, "jwtExpirationMs", ONE_HOUR_MS);

        user = User.reconstitute(new UserId("user-1"), "alice",
                "$2a$12$storedhash", List.of(UserRole.ROLE_USER), true);
    }

    @Nested
    @DisplayName("Token structure")
    class Structure {

        @Test
        @DisplayName("should emit a three-part compact JWS")
        void shouldEmitCompactJws() {
            String token = adapter.generateToken(user);

            assertEquals(3, token.split("\\.").length,
                    "a signed compact JWT must be header.payload.signature");
        }

        @Test
        @DisplayName("should set the username as the subject claim")
        void shouldSetSubject() {
            assertEquals("alice", parse(adapter.generateToken(user)).getSubject());
        }

        @Test
        @DisplayName("should carry the granted roles in the authorities claim")
        void shouldCarryAuthorities() {
            Claims claims = parse(adapter.generateToken(user));

            assertEquals(List.of("ROLE_USER"), claims.get("authorities", List.class));
        }

        @Test
        @DisplayName("should carry every role for a multi-role user")
        void shouldCarryAllAuthorities() {
            User admin = User.reconstitute(new UserId("user-2"), "root", "$2a$12$h",
                    List.of(UserRole.ROLE_USER, UserRole.ROLE_ADMIN), true);

            Claims claims = parse(adapter.generateToken(admin));

            assertEquals(List.of("ROLE_USER", "ROLE_ADMIN"), claims.get("authorities", List.class));
        }

        @Test
        @DisplayName("should sign with HS256")
        void shouldSignWithHs256() {
            String token = adapter.generateToken(user);

            String header = Jwts.parser().verifyWith(key(SECRET)).build()
                    .parseSignedClaims(token).getHeader().getAlgorithm();
            assertEquals("HS256", header);
        }
    }

    @Nested
    @DisplayName("Claim hygiene")
    class ClaimHygiene {

        @Test
        @DisplayName("should never embed the password hash in the token")
        void shouldNotLeakPasswordHash() {
            String token = adapter.generateToken(user);

            assertFalse(token.contains("storedhash"));
            assertFalse(parse(token).values().toString().contains("storedhash"));
        }

        @Test
        @DisplayName("should not embed the internal user id")
        void shouldNotLeakUserId() {
            Claims claims = parse(adapter.generateToken(user));

            assertFalse(claims.values().toString().contains("user-1"),
                    "the internal identifier is not needed by clients and widens the blast radius");
        }
    }

    @Nested
    @DisplayName("Expiry")
    class Expiry {

        @Test
        @DisplayName("should set issuedAt and expiration")
        void shouldSetTimestamps() {
            Claims claims = parse(adapter.generateToken(user));

            assertNotNull(claims.getIssuedAt());
            assertNotNull(claims.getExpiration());
        }

        @Test
        @DisplayName("should expire roughly one hour after issuance by default")
        void shouldHonourConfiguredTtl() {
            Claims claims = parse(adapter.generateToken(user));

            long ttlMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
            assertTrue(Math.abs(ttlMs - ONE_HOUR_MS) <= 1000,
                    "expected a ~1 h TTL but the token lives for " + ttlMs + " ms");
        }

        @Test
        @DisplayName("should honour a shortened expiration setting")
        void shouldHonourShortTtl() {
            ReflectionTestUtils.setField(adapter, "jwtExpirationMs", 60_000L);

            Claims claims = parse(adapter.generateToken(user));

            long ttlMs = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
            assertTrue(Math.abs(ttlMs - 60_000L) <= 1000);
        }

        @Test
        @DisplayName("an already-expired token should be rejected by the parser")
        void shouldRejectExpiredToken() {
            ReflectionTestUtils.setField(adapter, "jwtExpirationMs", -60_000L);
            String token = adapter.generateToken(user);

            assertThrows(JwtException.class, () -> parse(token));
        }

        @Test
        @DisplayName("should not issue a token that is already expired under the default TTL")
        void defaultTokenShouldBeInTheFuture() {
            Claims claims = parse(adapter.generateToken(user));

            assertTrue(claims.getExpiration().after(new Date()));
        }
    }

    @Nested
    @DisplayName("Signature integrity")
    class SignatureIntegrity {

        @Test
        @DisplayName("should reject a token verified with a different secret")
        void shouldRejectForeignSecret() {
            String token = adapter.generateToken(user);
            SecretKey attackerKey = key("a-completely-different-secret-key-32-bytes");

            assertThrows(JwtException.class, () -> Jwts.parser()
                    .verifyWith(attackerKey)
                    .build()
                    .parseSignedClaims(token));
        }

        @Test
        @DisplayName("should reject a token whose payload has been tampered with")
        void shouldRejectTamperedPayload() {
            String[] parts = adapter.generateToken(user).split("\\.");
            String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"sub\":\"admin\"}".getBytes());
            String forged = parts[0] + "." + forgedPayload + "." + parts[2];

            assertThrows(JwtException.class, () -> parse(forged));
        }
    }
}

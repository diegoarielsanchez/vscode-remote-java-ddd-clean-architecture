package com.das.order.application;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test for order-application — this module had no automated tests at all
 * until now. {@code @WebMvcTest} does not isolate cleanly here because
 * {@link OrderApplication} carries {@code @EnableJpaRepositories} directly (not in a
 * separate {@code @Configuration} class), so the slice still tries to wire the
 * generated repository beans without an {@code entityManagerFactory}. Boots the full
 * context against H2 instead, matching {@code MedicalSalesRepControllerTest}'s
 * established recipe for this exact situation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        // Replace PostgreSQL with H2
        "spring.datasource.url=jdbc:h2:mem:ordertest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        // Disable RabbitMQ auto-configuration (no broker needed in tests)
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration",
        // Disable Eureka (no service registry needed in tests)
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        // JWT secret satisfying the 32-char minimum enforced by JwtSecretValidator
        "jwt.secret=test-secret-key-for-integration-tests-only-32chars"
})
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/orders/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unmappedPathIsDenied() throws Exception {
        mockMvc.perform(get("/api/v1/orders/admin-only-not-a-real-path"))
                .andExpect(status().isForbidden());
    }

    // ── ROLE_AGENT: only the AI Agent Service may approve or reject orders (OWASP A01) ──

    private static final String JWT_SECRET = "test-secret-key-for-integration-tests-only-32chars";

    /** Signs a token exactly like identity-service, so the real JwtAuthenticationFilter is exercised. */
    private static String bearer(String... authorities) {
        return bearerForRep(null, authorities);
    }

    /** As {@link #bearer}, plus the msr_id claim identity-service adds for an account linked to an MSR. */
    private static String bearerForRep(String msrId, String... authorities) {
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

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void regularUserCannotCallAgentOnlyEndpoint(String action) throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + UUID.randomUUID() + "/" + action)
                        .header("Authorization", bearer("ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"approve", "reject"})
    void agentPassesAuthorizationOnAgentOnlyEndpoint(String action) throws Exception {
        // Past the security chain the handler may still say 400/404 for this random id —
        // what matters is that authorization no longer refuses the request.
        int status = mockMvc.perform(post("/api/v1/orders/" + UUID.randomUUID() + "/" + action)
                        .header("Authorization", bearer("ROLE_USER", "ROLE_AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(401, status);
        assertNotEquals(403, status);
    }

    @Test
    void regularUserCanStillReadById() throws Exception {
        int status = mockMvc.perform(get("/api/v1/orders/" + UUID.randomUUID())
                        .header("Authorization", bearer("ROLE_USER")))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, status, "ROLE_AGENT must only gate the agent-only endpoints");
    }

    // ── confirm-delivery: the AI Agent Service or the order's own MSR (OWASP A01) ──
    // Whether the MSR actually owns the order is checked in ConfirmOrderDeliveryUseCase.

    @Test
    void userNotLinkedToAnMsrCannotConfirmDelivery() throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + UUID.randomUUID() + "/confirm-delivery")
                        .header("Authorization", bearer("ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void msrAndAgentPassAuthorizationOnConfirmDelivery() throws Exception {
        for (String token : List.of(bearerForRep(UUID.randomUUID().toString(), "ROLE_USER"),
                                    bearer("ROLE_USER", "ROLE_AGENT"))) {
            int status = mockMvc.perform(post("/api/v1/orders/" + UUID.randomUUID() + "/confirm-delivery")
                            .header("Authorization", token))
                    .andReturn().getResponse().getStatus();
            assertNotEquals(401, status);
            assertNotEquals(403, status);
        }
    }
}

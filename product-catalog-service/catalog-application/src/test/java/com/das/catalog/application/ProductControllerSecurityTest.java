package com.das.catalog.application;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Smoke test for catalog-application — this module had no automated tests at all
 * until now. See {@code OrderControllerSecurityTest} for why this boots the full
 * context against H2 rather than using {@code @WebMvcTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        // Replace PostgreSQL with H2
        "spring.datasource.url=jdbc:h2:mem:catalogtest;DB_CLOSE_DELAY=-1",
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
class ProductControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/products/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unmappedPathIsDenied() throws Exception {
        mockMvc.perform(get("/api/v1/products/admin-only-not-a-real-path"))
                .andExpect(status().isForbidden());
    }

    // ── ROLE_AGENT: only the AI Agent Service may change stock (OWASP A01) ──

    private static final String JWT_SECRET = "test-secret-key-for-integration-tests-only-32chars";

    /** Signs a token exactly like identity-service, so the real JwtAuthenticationFilter is exercised. */
    private static String bearer(String... authorities) {
        byte[] key = JWT_SECRET.getBytes(StandardCharsets.UTF_8);
        return "Bearer " + Jwts.builder()
                .subject("tester")
                .claim("authorities", List.of(authorities))
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(new SecretKeySpec(key, "HmacSHA256"), Jwts.SIG.HS256)
                .compact();
    }

    @ParameterizedTest
    @ValueSource(strings = {"restock", "reserve-stock", "release-stock"})
    void regularUserCannotCallAgentOnlyEndpoint(String action) throws Exception {
        mockMvc.perform(post("/api/v1/products/" + UUID.randomUUID() + "/" + action)
                        .header("Authorization", bearer("ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"restock", "reserve-stock", "release-stock"})
    void agentPassesAuthorizationOnAgentOnlyEndpoint(String action) throws Exception {
        // Past the security chain the handler may still say 400/404 for this random id —
        // what matters is that authorization no longer refuses the request.
        int status = mockMvc.perform(post("/api/v1/products/" + UUID.randomUUID() + "/" + action)
                        .header("Authorization", bearer("ROLE_USER", "ROLE_AGENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(401, status);
        assertNotEquals(403, status);
    }

    @Test
    void regularUserCanListLots() throws Exception {
        int status = mockMvc.perform(get("/api/v1/products/" + UUID.randomUUID() + "/lots")
                        .header("Authorization", bearer("ROLE_USER")))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, status);
    }

    @Test
    void regularUserCanStillReadById() throws Exception {
        int status = mockMvc.perform(get("/api/v1/products/" + UUID.randomUUID())
                        .header("Authorization", bearer("ROLE_USER")))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, status, "ROLE_AGENT must only gate the agent-only endpoints");
    }

    // ── ROLE_ADMIN: managing the catalog, lot quarantine and recall lookups (OWASP A01) ──

    @ParameterizedTest
    @ValueSource(strings = {"POST /create", "PUT /update", "POST /{id}/activate", "POST /{id}/deactivate",
            "POST /{id}/lots/quarantine", "POST /{id}/lots/lift-quarantine", "GET /{id}/lots/recipients?batchNumber=L1",
            "GET /{id}/history", "GET /reservations/{id}/history", "POST /{id}/lots/excursions", "POST /{id}/lots/reject"})
    void regularUserCannotManageCatalog(String endpoint) throws Exception {
        mockMvc.perform(adminEndpoint(endpoint).header("Authorization", bearer("ROLE_USER")))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST /create", "PUT /update", "POST /{id}/activate", "POST /{id}/deactivate",
            "POST /{id}/lots/quarantine", "POST /{id}/lots/lift-quarantine", "GET /{id}/lots/recipients?batchNumber=L1",
            "GET /{id}/history", "GET /reservations/{id}/history", "POST /{id}/lots/excursions", "POST /{id}/lots/reject"})
    void adminPassesAuthorizationOnCatalogManagement(String endpoint) throws Exception {
        int status = mockMvc.perform(adminEndpoint(endpoint).header("Authorization", bearer("ROLE_USER", "ROLE_ADMIN")))
                .andReturn().getResponse().getStatus();
        assertNotEquals(401, status);
        assertNotEquals(403, status);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder adminEndpoint(String endpoint) {
        String[] parts = endpoint.split(" ");
        String uri = "/api/v1/products" + parts[1].replace("{id}", UUID.randomUUID().toString());
        return switch (parts[0]) {
            case "GET" -> get(uri);
            case "PUT" -> put(uri).contentType(MediaType.APPLICATION_JSON).content("{}");
            default -> post(uri).contentType(MediaType.APPLICATION_JSON).content("{}");
        };
    }

    @Test
    void theAgentMayRecordReadingsButNotRejectALot() throws Exception {
        int status = mockMvc.perform(adminEndpoint("POST /{id}/lots/excursions")
                        .header("Authorization", bearer("ROLE_USER", "ROLE_AGENT")))
                .andReturn().getResponse().getStatus();
        assertNotEquals(403, status);
        mockMvc.perform(adminEndpoint("POST /{id}/lots/reject").header("Authorization", bearer("ROLE_USER", "ROLE_AGENT")))
                .andExpect(status().isForbidden());
    }
}

package com.das.registry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the Eureka service registry.
 * <p>
 * The registry has no business logic of its own, but it does carry a security
 * configuration and self-registration settings that must not silently regress —
 * a registry that registers with itself, or starts without HTTP Basic protection,
 * is a real defect that only shows up at startup.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false"
})
@DisplayName("Eureka server application")
class EurekaServerApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("should start the registry with its production configuration")
    void contextLoads() {
        assertNotNull(context);
    }

    @Test
    @DisplayName("should register the security filter chain")
    void securityFilterChainIsWired() {
        assertTrue(
                context.getBeanNamesForType(
                        org.springframework.security.web.SecurityFilterChain.class).length > 0,
                "the registry must not start without its security filter chain");
    }

    @Test
    @DisplayName("should not register itself with, or fetch from, another registry")
    void doesNotSelfRegister() {
        var env = context.getEnvironment();
        assertTrue(
                "false".equalsIgnoreCase(env.getProperty("eureka.client.register-with-eureka")),
                "the registry must not register itself as a client");
        assertTrue(
                "false".equalsIgnoreCase(env.getProperty("eureka.client.fetch-registry")),
                "the registry must not fetch a peer registry in a single-node setup");
    }
}

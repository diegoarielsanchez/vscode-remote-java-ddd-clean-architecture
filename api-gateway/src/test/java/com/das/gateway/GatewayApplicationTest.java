package com.das.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test for the API Gateway.
 * <p>
 * The gateway carries no business logic, so there is little to unit test — but a broken
 * route definition, an unparseable YAML filter or a security bean that fails to construct
 * are all startup failures. Booting the real context in CI is what catches them.
 * <p>
 * Service discovery is disabled here: the {@code lb://} route URIs are resolved lazily at
 * request time, so the context loads without a running Eureka registry.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "spring.cloud.discovery.enabled=false"
})
@DisplayName("Gateway application")
class GatewayApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("should start with the production route and security configuration")
    void contextLoads() {
        assertNotNull(context);
    }

    @Test
    @DisplayName("should register the security filter chain")
    void securityFilterChainIsWired() {
        assertTrue(
                context.getBeanNamesForType(
                        org.springframework.security.web.server.SecurityWebFilterChain.class).length > 0,
                "the gateway must not start without its security filter chain");
    }

    @Test
    @DisplayName("should register the custom JWT and rate-limit filters")
    void customFiltersAreWired() {
        assertNotNull(context.getBean(com.das.gateway.filter.JwtAuthenticationFilter.class));
        assertNotNull(context.getBean(com.das.gateway.filter.RateLimitFilter.class));
    }
}

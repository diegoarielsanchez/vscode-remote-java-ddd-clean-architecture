package com.das.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
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

    @Autowired
    private WebTestClient webTestClient;

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

    @Test
    @DisplayName("should not let an anonymous caller add a route through the actuator")
    void actuatorCannotAddRoutes() {
        webTestClient.post().uri("/actuator/gateway/routes/injected")
                .bodyValue("{\"uri\":\"http://169.254.169.254\",\"predicates\":[]}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus().is4xxClientError();
    }

    @Test
    @DisplayName("should not expose route or metrics actuator endpoints anonymously")
    void actuatorReadEndpointsAreClosed() {
        webTestClient.get().uri("/actuator/gateway/routes").exchange().expectStatus().is4xxClientError();
        webTestClient.get().uri("/actuator/metrics").exchange().expectStatus().is4xxClientError();
    }
}

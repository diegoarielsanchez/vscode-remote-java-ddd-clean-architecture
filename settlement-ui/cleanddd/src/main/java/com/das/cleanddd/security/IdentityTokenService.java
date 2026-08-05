package com.das.cleanddd.security;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Obtains and caches a JWT bearer token from identity-service, so the UI can
 * call downstream APIs (settlement-service, msr-service, etc.) on behalf of a
 * fixed service account.
 * <p>
 * Uses the dedicated {@code identityRestTemplate} bean (not the shared one used for
 * business API calls) to avoid the auth interceptor being applied to the
 * login call itself. Both beans are {@code @LoadBalanced}, resolving logical
 * Eureka service names instead of hard-coded host:port values.
 */
@Service
public class IdentityTokenService {

    private static final Logger log = LoggerFactory.getLogger(IdentityTokenService.class);

    /** Refresh a bit before actual expiry to avoid using a token that expires mid-request. */
    private static final long EXPIRY_SAFETY_MARGIN_SECONDS = 30;

    private final RestTemplate restTemplate;

    private final String identityBaseUrl;
    private final String username;
    private final String password;
    private final long tokenTtlSeconds;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.MIN;

    public IdentityTokenService(
            @Qualifier("identityRestTemplate") RestTemplate identityRestTemplate,
            @Value("${identity.service.base-url:http://localhost:8090}") String identityBaseUrl,
            @Value("${identity.client.username:user}") String username,
            @Value("${identity.client.password:}") String password,
            @Value("${identity.client.token-ttl-seconds:3600}") long tokenTtlSeconds) {
        this.restTemplate = identityRestTemplate;
        this.identityBaseUrl = identityBaseUrl;
        this.username = username;
        this.password = password;
        this.tokenTtlSeconds = tokenTtlSeconds;
    }

    /** Returns a cached valid token, transparently refreshing it when expired. */
    public synchronized String getToken() {
        if (cachedToken == null || Instant.now().isAfter(expiresAt)) {
            refreshToken();
        }
        return cachedToken;
    }

    private void refreshToken() {
        try {
            Map<String, String> body = Map.of("username", username, "password", password);
            LoginResponse response = restTemplate.postForObject(
                    identityBaseUrl + "/auth/login", body, LoginResponse.class);
            if (response == null || response.token() == null) {
                throw new IllegalStateException("identity-service returned an empty token");
            }
            cachedToken = response.token();
            expiresAt = Instant.now().plusSeconds(Math.max(1, tokenTtlSeconds - EXPIRY_SAFETY_MARGIN_SECONDS));
        } catch (RestClientException ex) {
            log.error("Failed to obtain token from identity-service at {}: {}", identityBaseUrl, ex.getMessage());
            throw ex;
        }
    }

    private record LoginResponse(String token, String username, java.util.List<String> roles) {}
}

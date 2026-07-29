package com.das.cleanddd.security;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

/**
 * Attaches a "Bearer &lt;token&gt;" Authorization header to every outgoing
 * request made through the shared {@code RestTemplate}, so downstream
 * services (settlement-service, msr-service, etc.) receive a valid JWT
 * instead of relying on those endpoints being permitAll().
 */
@Component
public class AuthTokenInterceptor implements ClientHttpRequestInterceptor {

    private final IdentityTokenService identityTokenService;

    public AuthTokenInterceptor(IdentityTokenService identityTokenService) {
        this.identityTokenService = identityTokenService;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String token = identityTokenService.getToken();
        request.getHeaders().setBearerAuth(token);
        return execution.execute(request, body);
    }
}

package com.das.gateway.filter;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;

/**
 * Reactive GlobalFilter that validates the JWT bearer token on every request
 * before routing to a downstream microservice.
 *
 * Excluded paths (no token required): /actuator/health, /actuator/info
 */
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final List<String> EXCLUDED_PREFIXES = List.of(
            "/actuator/health",
            "/actuator/info"
    );

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        if (EXCLUDED_PREFIXES.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or malformed Authorization header for path: {}", path);
            return unauthorizedResponse(exchange, "Missing or malformed Authorization header");
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = extractClaims(token);
            // Forward subject as X-Auth-User header to downstream services
            ServerWebExchange mutated = exchange.mutate()
                    .request(r -> r.header("X-Auth-User", claims.getSubject()))
                    .build();
            return chain.filter(mutated);
        } catch (Exception e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            return unauthorizedResponse(exchange, "Invalid or expired token");
        }
    }

    private Claims extractClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"error\":\"Unauthorized\",\"message\":\"" + message + "\"}").getBytes();
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
    }

    @Override
    public int getOrder() {
        // Run before routing (order -2) but after rate limiting (order -1)
        return -2;
    }
}

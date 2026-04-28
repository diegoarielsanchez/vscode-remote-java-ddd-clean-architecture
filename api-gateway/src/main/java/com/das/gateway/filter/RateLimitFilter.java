package com.das.gateway.filter;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import reactor.core.publisher.Mono;

/**
 * Reactive GlobalFilter that enforces per-IP rate limiting using bucket4j.
 * Each client IP gets a token bucket with a configurable capacity and refill rate.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Value("${gateway.ratelimit.capacity:20}")
    private int capacity;

    @Value("${gateway.ratelimit.refill-tokens:20}")
    private int refillTokens;

    @Value("${gateway.ratelimit.refill-seconds:60}")
    private int refillSeconds;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String clientIp = resolveClientIp(exchange);
        Bucket bucket = buckets.computeIfAbsent(clientIp, this::newBucket);

        if (bucket.tryConsume(1)) {
            return chain.filter(exchange);
        }

        log.warn("Rate limit exceeded for IP: {}", clientIp);
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        exchange.getResponse().getHeaders().add("X-RateLimit-Retry-After", String.valueOf(refillSeconds));
        byte[] body = "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please retry later.\"}".getBytes();
        Mono<org.springframework.core.io.buffer.DataBuffer> dataBuffer = 
            Mono.just(exchange.getResponse().bufferFactory().wrap(body));
        return exchange.getResponse().writeWith(dataBuffer);
    }

    private Bucket newBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(
                capacity,
                Refill.greedy(refillTokens, Duration.ofSeconds(refillSeconds))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Take first IP from the chain (actual client)
            return forwarded.split(",")[0].trim();
        }
        var address = exchange.getRequest().getRemoteAddress();
        return address != null ? address.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        return -1;
    }
}

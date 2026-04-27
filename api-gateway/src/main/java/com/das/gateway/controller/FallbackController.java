package com.das.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Returns a structured error when a circuit breaker trips
 * (downstream service is unavailable).
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    @RequestMapping("/unavailable")
    public Mono<ResponseEntity<Map<String, String>>> unavailable() {
        return Mono.just(
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                    "error", "Service Unavailable",
                    "message", "The requested service is temporarily unavailable. Please try again later."
                ))
        );
    }
}

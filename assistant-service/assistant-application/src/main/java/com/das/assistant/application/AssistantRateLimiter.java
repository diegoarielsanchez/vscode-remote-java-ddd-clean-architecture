package com.das.assistant.application;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

/**
 * Caps how many messages each rep may send the assistant per minute. Every message costs model tokens and can run
 * several tool calls, so an unbounded caller is a cost and availability risk (OWASP LLM10).
 */
@Component
public class AssistantRateLimiter {

    private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int messagesPerMinute;

    public AssistantRateLimiter(@Value("${assistant.rate-limit.messages-per-minute:10}") int messagesPerMinute) {
        this.messagesPerMinute = messagesPerMinute;
    }

    public void acquire(String medicalSalesRepId) {
        Bucket bucket = buckets.computeIfAbsent(medicalSalesRepId, id -> Bucket.builder()
                .addLimit(Bandwidth.simple(messagesPerMinute, Duration.ofMinutes(1)))
                .build());
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException("Too many messages to the assistant; wait a minute.");
        }
    }
}

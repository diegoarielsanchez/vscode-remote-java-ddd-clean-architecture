package com.das.infra.service.visit.events;

/**
 * The message is not a valid integration event. Never retried: it goes straight to the
 * dead-letter queue. Messages describe the problem without echoing untrusted values.
 */
public class InvalidIntegrationEventException extends RuntimeException {

    public InvalidIntegrationEventException(String message) {
        super(message);
    }
}

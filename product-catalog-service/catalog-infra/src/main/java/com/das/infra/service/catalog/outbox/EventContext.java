package com.das.infra.service.catalog.outbox;

/**
 * Request context copied into integration events: a correlation id for tracing and the actor
 * (authenticated principal name) for audit. Never credentials. Implemented by the application
 * layer, which knows about HTTP/security; {@link #NONE} is used when no implementation exists.
 */
public interface EventContext {

    String correlationId();

    String actor();

    EventContext NONE = new EventContext() {
        @Override public String correlationId() { return null; }
        @Override public String actor() { return null; }
    };
}

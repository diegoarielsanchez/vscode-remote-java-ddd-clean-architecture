package com.das.hcp.application.config;

import org.slf4j.MDC;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.das.infra.service.healthcareprof.outbox.EventContext;

/**
 * Supplies integration events with the current trace id (put in the MDC by the OpenTelemetry
 * agent) and the authenticated principal's name (the JWT subject) for audit. No tokens or
 * credentials ever leave this class.
 */
@Component
public class RequestEventContext implements EventContext {

    @Override
    public String correlationId() {
        return MDC.get("trace_id");
    }

    @Override
    public String actor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth.getName();
    }
}

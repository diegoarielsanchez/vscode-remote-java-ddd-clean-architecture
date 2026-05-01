package com.das.infrapostgresql.service.healthcareprof;

import java.time.Instant;

public record HcpEventPayload(
        String eventType,
        String id,
        String name,
        String surname,
        String email,
        Boolean active,
        String occurredAt
) {
    public static HcpEventPayload of(String eventType, String id, String name,
                                     String surname, String email, Boolean active) {
        return new HcpEventPayload(eventType, id, name, surname, email, active,
                Instant.now().toString());
    }
}

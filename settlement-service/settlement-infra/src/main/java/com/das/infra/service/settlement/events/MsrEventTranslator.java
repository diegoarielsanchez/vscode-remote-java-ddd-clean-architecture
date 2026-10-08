package com.das.infra.service.settlement.events;

import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Anti-corruption layer for the {@code msr.events} contract (schema version 1). Settlement only
 * needs a rep's active status, so names in {@code data} are deliberately not read or stored.
 */
@Component
public class MsrEventTranslator {

    private static final Set<String> TYPES = Set.of("msr.created", "msr.updated", "msr.activated", "msr.deactivated");

    public MsrStatusChange translate(byte[] body) {
        EnvelopeParser.Envelope e = EnvelopeParser.parse(body, "msr", TYPES);
        boolean active = switch (e.eventType()) {
            case "msr.activated" -> true;
            case "msr.deactivated" -> false;
            default -> e.requiredBoolean("active"); // created / updated carry the current status
        };
        return new MsrStatusChange(e.eventId(), e.eventType(), e.aggregateId(), e.version(), active);
    }
}

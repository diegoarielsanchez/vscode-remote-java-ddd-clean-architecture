package com.das.infra.service.visit.events;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Anti-corruption layer for the {@code hcp.events} contract (schema version 1): validates the
 * envelope ({@link EnvelopeParser}) and translates it into visit-service's {@link HcpChange}.
 */
@Component
public class HcpEventTranslator {

    static final int MAX_MESSAGE_BYTES = EnvelopeParser.MAX_MESSAGE_BYTES;

    private static final Map<String, HcpChange.Type> TYPES = Map.of(
            "hcp.created", HcpChange.Type.CREATED,
            "hcp.updated", HcpChange.Type.UPDATED,
            "hcp.activated", HcpChange.Type.ACTIVATED,
            "hcp.deactivated", HcpChange.Type.DEACTIVATED);

    public HcpChange translate(byte[] body) {
        EnvelopeParser.Envelope e = EnvelopeParser.parse(body, "hcp", TYPES.keySet());
        HcpChange.Type type = TYPES.get(e.eventType());
        return switch (type) {
            case CREATED, UPDATED -> new HcpChange(e.eventId(), type, e.aggregateId(), e.version(),
                    e.name("name"), e.name("surname"), e.requiredBoolean("active"));
            case ACTIVATED -> new HcpChange(e.eventId(), type, e.aggregateId(), e.version(), null, null, true);
            case DEACTIVATED -> new HcpChange(e.eventId(), type, e.aggregateId(), e.version(), null, null, false);
        };
    }
}

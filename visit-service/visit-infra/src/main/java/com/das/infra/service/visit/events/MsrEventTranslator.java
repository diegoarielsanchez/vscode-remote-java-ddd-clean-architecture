package com.das.infra.service.visit.events;

import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Anti-corruption layer for the {@code msr.events} contract (schema version 1): validates the
 * envelope ({@link EnvelopeParser}) and translates it into visit-service's {@link MsrChange}.
 */
@Component
public class MsrEventTranslator {

    static final int MAX_MESSAGE_BYTES = EnvelopeParser.MAX_MESSAGE_BYTES;

    private static final Map<String, MsrChange.Type> TYPES = Map.of(
            "msr.created", MsrChange.Type.CREATED,
            "msr.updated", MsrChange.Type.UPDATED,
            "msr.activated", MsrChange.Type.ACTIVATED,
            "msr.deactivated", MsrChange.Type.DEACTIVATED);

    public MsrChange translate(byte[] body) {
        EnvelopeParser.Envelope e = EnvelopeParser.parse(body, "msr", TYPES.keySet());
        MsrChange.Type type = TYPES.get(e.eventType());
        return switch (type) {
            case CREATED, UPDATED -> new MsrChange(e.eventId(), type, e.aggregateId(), e.version(),
                    e.name("name"), e.name("surname"), e.requiredBoolean("active"));
            case ACTIVATED -> new MsrChange(e.eventId(), type, e.aggregateId(), e.version(), null, null, true);
            case DEACTIVATED -> new MsrChange(e.eventId(), type, e.aggregateId(), e.version(), null, null, false);
        };
    }
}

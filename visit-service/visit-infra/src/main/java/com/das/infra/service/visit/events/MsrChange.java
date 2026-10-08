package com.das.infra.service.visit.events;

import java.util.UUID;

/**
 * A validated {@code msr.*} integration event, translated into visit-service's own terms.
 * {@code name}/{@code surname} are present for created/updated; {@code active} is always present.
 */
public record MsrChange(UUID eventId, Type type, String msrId, long version,
                        String name, String surname, boolean active) {

    public enum Type { CREATED, UPDATED, ACTIVATED, DEACTIVATED }

    public boolean carriesDetails() {
        return type == Type.CREATED || type == Type.UPDATED;
    }
}

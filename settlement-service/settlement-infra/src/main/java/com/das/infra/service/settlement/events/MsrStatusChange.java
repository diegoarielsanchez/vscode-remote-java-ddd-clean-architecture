package com.das.infra.service.settlement.events;

import java.util.UUID;

/** A validated {@code msr.*} event reduced to what settlement-service needs: is the rep active. */
public record MsrStatusChange(UUID eventId, String eventType, String msrId, long version, boolean active) {
}

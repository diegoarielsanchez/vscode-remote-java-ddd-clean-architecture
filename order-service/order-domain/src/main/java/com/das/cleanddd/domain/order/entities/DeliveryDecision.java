package com.das.cleanddd.domain.order.entities;

import java.time.Instant;

/** Quality's decision on a delivery quarantined for a transport break: who decided, why and when. */
public record DeliveryDecision(String decidedBy, String reason, Instant decidedAt) {
}

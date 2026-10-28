package com.das.cleanddd.domain.order.usecases.dtos;

/**
 * Quality's decision on a quarantined delivery. {@code decidedBy} is always the authenticated principal, never a
 * request value; {@code reason} is required, at most 500 characters.
 */
public record DecideDeliveryInputDTO(
    String orderId,
    String decidedBy,
    String reason
) {}

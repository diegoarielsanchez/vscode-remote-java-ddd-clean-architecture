package com.das.cleanddd.domain.order.usecases.dtos;

/** {@code rejectedBy} is always populated by the controller from the authenticated principal — never from a client-supplied body field. */
public record RejectOrderInputDTO(
    String orderId,
    String rejectedBy,
    String reason
) {}

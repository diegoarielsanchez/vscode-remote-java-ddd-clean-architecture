package com.das.cleanddd.domain.order.usecases.dtos;

/** {@code approvedBy} is always populated by the controller from the authenticated principal — never from a client-supplied body field. */
public record ApproveOrderInputDTO(
    String orderId,
    String approvedBy
) {}

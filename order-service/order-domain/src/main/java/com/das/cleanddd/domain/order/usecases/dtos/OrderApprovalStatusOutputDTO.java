package com.das.cleanddd.domain.order.usecases.dtos;

public record OrderApprovalStatusOutputDTO(
    boolean approved,
    String status
) {}

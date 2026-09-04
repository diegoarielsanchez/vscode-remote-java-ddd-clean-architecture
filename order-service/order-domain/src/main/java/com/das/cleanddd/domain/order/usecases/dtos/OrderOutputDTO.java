package com.das.cleanddd.domain.order.usecases.dtos;

import java.math.BigDecimal;
import java.util.List;

public record OrderOutputDTO(
    String id,
    String medicalSalesRepId,
    String status,
    List<OrderLineOutputDTO> lines,
    BigDecimal totalAmount,
    String approvedBy,
    String rejectedBy,
    String rejectionReason,
    String createdAt,
    String approvedAt,
    String rejectedAt,
    String deliveredAt
) {}

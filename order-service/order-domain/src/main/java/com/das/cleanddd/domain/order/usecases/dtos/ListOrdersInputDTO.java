package com.das.cleanddd.domain.order.usecases.dtos;

public record ListOrdersInputDTO(
    String medicalSalesRepId,
    int page,
    int pageSize
) {}

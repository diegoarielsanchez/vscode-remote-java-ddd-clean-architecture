package com.das.cleanddd.domain.order.usecases.dtos;

/**
 * @param orderId                   the order to mark delivered
 * @param requiredMedicalSalesRepId when set, the order must belong to this MSR (a rep confirming
 *                                  their own delivery); {@code null} for trusted callers — the AI
 *                                  Agent Service or delivery-service's confirmation event
 */
public record ConfirmOrderDeliveryInputDTO(
    String orderId,
    String requiredMedicalSalesRepId
) {}

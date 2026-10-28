package com.das.cleanddd.domain.order.usecases.dtos;

import java.math.BigDecimal;

/**
 * @param orderId                   the order to mark delivered
 * @param requiredMedicalSalesRepId when set, the order must belong to this MSR (a rep confirming
 *                                  their own delivery); {@code null} for trusted callers — the AI
 *                                  Agent Service or delivery-service's confirmation event
 * @param transport                 the receiver's transport report; required when any line has storage
 *                                  conditions, optional otherwise
 */
public record ConfirmOrderDeliveryInputDTO(
    String orderId,
    String requiredMedicalSalesRepId,
    TransportInputDTO transport
) {

    public ConfirmOrderDeliveryInputDTO(String orderId, String requiredMedicalSalesRepId) {
        this(orderId, requiredMedicalSalesRepId, null);
    }

    /**
     * Whether transport kept the products' storage conditions ({@code conditionsMaintained}, required), with the
     * shipment logger's readings when there is one (°C, % RH, light exposure) and an optional note.
     */
    public record TransportInputDTO(
        Boolean conditionsMaintained,
        BigDecimal minTemperatureC,
        BigDecimal maxTemperatureC,
        Integer maxHumidityPercent,
        Boolean lightExposed,
        String note
    ) {

        public boolean hasReadings() {
            return minTemperatureC != null || maxTemperatureC != null || maxHumidityPercent != null
                    || lightExposed != null;
        }
    }
}

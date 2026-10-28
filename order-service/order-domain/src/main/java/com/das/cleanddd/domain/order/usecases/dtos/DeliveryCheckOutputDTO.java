package com.das.cleanddd.domain.order.usecases.dtos;

import java.math.BigDecimal;
import java.util.List;

import com.das.cleanddd.domain.order.entities.DeliveryCheck;
import com.das.cleanddd.domain.order.entities.DeliveryDecision;

/**
 * The receiver's transport report and, for a quarantined delivery, Quality's decision ({@code decidedBy},
 * {@code decisionReason}, {@code decidedAt}; {@code null} until decided).
 */
public record DeliveryCheckOutputDTO(
    boolean conditionsMaintained,
    BigDecimal minTemperatureC,
    BigDecimal maxTemperatureC,
    Integer maxHumidityPercent,
    Boolean lightExposed,
    String note,
    List<String> breaches,
    String decidedBy,
    String decisionReason,
    String decidedAt
) {

    public DeliveryCheckOutputDTO {
        breaches = breaches == null ? List.of() : List.copyOf(breaches);
    }

    public static DeliveryCheckOutputDTO from(DeliveryCheck check, DeliveryDecision decision) {
        if (check == null) {
            return null;
        }
        return new DeliveryCheckOutputDTO(check.conditionsMaintained(),
                check.readings() == null ? null : check.readings().minTemperatureC(),
                check.readings() == null ? null : check.readings().maxTemperatureC(),
                check.readings() == null ? null : check.readings().maxHumidityPercent(),
                check.readings() == null ? null : check.readings().lightExposed(),
                check.note(), check.breaches(),
                decision == null ? null : decision.decidedBy(),
                decision == null ? null : decision.reason(),
                decision == null || decision.decidedAt() == null ? null : decision.decidedAt().toString());
    }
}

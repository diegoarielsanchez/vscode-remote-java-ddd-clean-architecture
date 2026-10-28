package com.das.infra.service.order;

import java.math.BigDecimal;

import com.das.cleanddd.domain.shared.storage.StorageCondition;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * An order line's {@link StorageCondition}, as product-catalog-service reported it, in four columns. All null: the
 * product has none; Hibernate then loads the embeddable itself as null.
 */
@Embeddable
public class StorageConditionEmbeddable {

    @Column(name = "storage_min_temp_c", precision = 4, scale = 1)
    private BigDecimal minTemperatureC;

    @Column(name = "storage_max_temp_c", precision = 4, scale = 1)
    private BigDecimal maxTemperatureC;

    @Column(name = "storage_max_humidity")
    private Integer maxHumidityPercent;

    @Column(name = "storage_protect_from_light")
    private Boolean protectFromLight;

    protected StorageConditionEmbeddable() {
        // JPA
    }

    private StorageConditionEmbeddable(StorageCondition condition) {
        this.minTemperatureC = condition.minTemperatureC();
        this.maxTemperatureC = condition.maxTemperatureC();
        this.maxHumidityPercent = condition.maxHumidityPercent();
        this.protectFromLight = condition.protectFromLight();
    }

    public static StorageConditionEmbeddable of(StorageCondition condition) {
        return condition == null ? null : new StorageConditionEmbeddable(condition);
    }

    /** {@code null} when no temperature range is stored. */
    public static StorageCondition toDomain(StorageConditionEmbeddable stored) {
        if (stored == null || stored.minTemperatureC == null || stored.maxTemperatureC == null) {
            return null;
        }
        return new StorageCondition(stored.minTemperatureC, stored.maxTemperatureC, stored.maxHumidityPercent,
                Boolean.TRUE.equals(stored.protectFromLight));
    }
}

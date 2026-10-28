package com.das.infra.service.catalog;

import java.math.BigDecimal;

import com.das.cleanddd.domain.shared.storage.StorageCondition;

import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * What the {@code productById} cache stores: a plain copy of the product row, not the domain
 * aggregate. Keeps caching concerns out of the domain model and makes the cached form explicit.
 *
 * <p>Stored in Redis as typed JSON ({@link #redisSerializer()}): no Java serialization and no
 * class names in the payload, so reading the cache back cannot instantiate arbitrary types even if
 * Redis contents were tampered with (OWASP A08).
 *
 * <p>{@code stock} is the units on hand across the product's lots at the time of caching; every lot change evicts
 * the entry. Entries cached before {@code gtin} or {@code lotTracked} existed read back with them {@code null};
 * a {@code null} {@code lotTracked} means lot-tracked, as every product was then. Entries cached before storage
 * conditions existed read back without them.
 */
public record ProductSnapshot(String id, String name, String description, BigDecimal price, String unit,
                              Integer stock, Boolean active, String gtin, Boolean lotTracked,
                              BigDecimal storageMinTemperatureC, BigDecimal storageMaxTemperatureC,
                              Integer storageMaxHumidityPercent, Boolean storageProtectFromLight) {

    public static final String CACHE = "productById";

    static ProductSnapshot from(ProductEntity entity, int stock) {
        StorageConditionEmbeddable storage = entity.getStorageCondition();
        return new ProductSnapshot(entity.getId(), entity.getName(), entity.getDescription(), entity.getPrice(),
                entity.getUnit(), stock, entity.getActive(), entity.getGtin(), entity.getLotTracked(),
                storage == null ? null : storage.getMinTemperatureC(), storage == null ? null : storage.getMaxTemperatureC(),
                storage == null ? null : storage.getMaxHumidityPercent(),
                storage == null ? null : storage.getProtectFromLight());
    }

    /** {@code null} when none is on file. */
    public StorageCondition storageCondition() {
        if (storageMinTemperatureC == null || storageMaxTemperatureC == null) {
            return null;
        }
        return new StorageCondition(storageMinTemperatureC, storageMaxTemperatureC, storageMaxHumidityPercent,
                Boolean.TRUE.equals(storageProtectFromLight));
    }

    public boolean isLotTracked() {
        return !Boolean.FALSE.equals(lotTracked);
    }

    /** Typed JSON serializer for the {@code productById} Redis cache. */
    public static RedisSerializer<ProductSnapshot> redisSerializer() {
        return new Jackson2JsonRedisSerializer<>(ProductSnapshot.class);
    }
}

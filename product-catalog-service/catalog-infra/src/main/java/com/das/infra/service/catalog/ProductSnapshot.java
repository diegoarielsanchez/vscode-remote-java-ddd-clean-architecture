package com.das.infra.service.catalog;

import java.math.BigDecimal;

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
 * <p>{@code expirationDate} is kept as an ISO {@code yyyy-MM-dd} string, which the plain serializer handles
 * without a date/time module. Entries cached before these fields existed read back with them {@code null}.
 */
public record ProductSnapshot(String id, String name, String description, BigDecimal price, String unit,
                              Integer stock, Boolean active, String batchNumber, String serialNumber,
                              String expirationDate) {

    public static final String CACHE = "productById";

    static ProductSnapshot from(ProductEntity entity) {
        return new ProductSnapshot(entity.getId(), entity.getName(), entity.getDescription(), entity.getPrice(),
                entity.getUnit(), entity.getStock(), entity.getActive(), entity.getBatchNumber(),
                entity.getSerialNumber(),
                entity.getExpirationDate() == null ? null : entity.getExpirationDate().toString());
    }

    /** Typed JSON serializer for the {@code productById} Redis cache. */
    public static RedisSerializer<ProductSnapshot> redisSerializer() {
        return new Jackson2JsonRedisSerializer<>(ProductSnapshot.class);
    }
}

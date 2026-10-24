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
 * <p>{@code stock} is the units on hand across the product's lots at the time of caching; every lot change evicts
 * the entry. Entries cached before {@code gtin} or {@code lotTracked} existed read back with them {@code null};
 * a {@code null} {@code lotTracked} means lot-tracked, as every product was then.
 */
public record ProductSnapshot(String id, String name, String description, BigDecimal price, String unit,
                              Integer stock, Boolean active, String gtin, Boolean lotTracked) {

    public static final String CACHE = "productById";

    static ProductSnapshot from(ProductEntity entity, int stock) {
        return new ProductSnapshot(entity.getId(), entity.getName(), entity.getDescription(), entity.getPrice(),
                entity.getUnit(), stock, entity.getActive(), entity.getGtin(), entity.getLotTracked());
    }

    public boolean isLotTracked() {
        return !Boolean.FALSE.equals(lotTracked);
    }

    /** Typed JSON serializer for the {@code productById} Redis cache. */
    public static RedisSerializer<ProductSnapshot> redisSerializer() {
        return new Jackson2JsonRedisSerializer<>(ProductSnapshot.class);
    }
}

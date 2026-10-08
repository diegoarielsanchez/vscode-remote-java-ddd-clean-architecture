package com.das.infra.service.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

class ProductSnapshotTest {

    private final RedisSerializer<ProductSnapshot> serializer = ProductSnapshot.redisSerializer();

    @Test
    void roundTripsExactlyThroughTheRedisSerializer() {
        ProductSnapshot snapshot = new ProductSnapshot("7c3e2a1b-9d8f-4e6a-b5c4-3d2e1f0a9b87", "Amoxicillin 500mg",
                null, new BigDecimal("12.50"), "BOX", 10, true);

        ProductSnapshot back = serializer.deserialize(serializer.serialize(snapshot));

        assertEquals(snapshot, back);
        assertEquals("12.50", back.price().toPlainString());
    }

    @Test
    void storesPlainJsonWithoutTypeMetadata() {
        String json = new String(serializer.serialize(
                new ProductSnapshot("id-1", "Ibuprofen", "Analgesic", new BigDecimal("3.20"), "BOX", 5, false)),
                StandardCharsets.UTF_8);

        assertFalse(json.contains("@class") || json.contains("com.das") || json.contains("java."),
                "no class names in the cache payload: " + json);
    }
}

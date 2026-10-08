package com.das.infra.service.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.serializer.support.SerializationDelegate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductDescription;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductStock;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;

/**
 * The repository's caching evaluated by real Spring caching, with values stored <em>by value</em>
 * through the same typed JSON serializer Redis uses in production. Two production bugs hid because
 * tests had no cache manager (SpEL never evaluated) or stored references (serialization never
 * happened): {@code isEmpty()} on the unwrapped product, and a non-serializable cached aggregate.
 */
@DataJpaTest
@Import({SQLProductRepository.class, ProductSnapshotLookup.class, SQLProductRepositoryCachingTest.CachingConfig.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class SQLProductRepositoryCachingTest {

    @TestConfiguration
    @EnableCaching
    static class CachingConfig {
        /** Stores values as the bytes the production Redis serializer produces. */
        @Bean
        CacheManager cacheManager() {
            RedisSerializer<ProductSnapshot> json = ProductSnapshot.redisSerializer();
            SerializationDelegate byValue = new SerializationDelegate(
                    (value, out) -> out.write(json.serialize((ProductSnapshot) value)),
                    in -> json.deserialize(in.readAllBytes()));
            SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(java.util.List.of(
                    new ConcurrentMapCache(ProductSnapshot.CACHE, new ConcurrentHashMap<>(), false, byValue) {})); // protected ctor
            return manager;
        }
    }

    @Autowired private SQLProductRepository repository;
    @Autowired private CacheManager cacheManager;
    @MockitoSpyBean private ProductJpaRepository jpaRepository;

    private Product savedProduct() throws Exception {
        Product product = Product.create(null, new ProductName("Amoxicillin 500mg"), new ProductDescription("Antibiotic"),
                new ProductPrice(new BigDecimal("12.50")), new ProductUnit("BOX"), new ProductStock(10));
        repository.save(product);
        return product;
    }

    private Object cached(String id) {
        var wrapper = cacheManager.getCache(ProductSnapshot.CACHE).get(id);
        return wrapper == null ? null : wrapper.get();
    }

    @Test
    void cachesASnapshotAndServesTheNextReadWithoutTheDatabase() throws Exception {
        Product product = savedProduct();
        String id = product.getId().value();

        Product first = repository.findById(product.getId()).orElseThrow();
        assertInstanceOf(ProductSnapshot.class, cached(id), "the cache holds the infra snapshot, not the aggregate");

        clearInvocations(jpaRepository);
        Product second = repository.findById(product.getId()).orElseThrow();

        verify(jpaRepository, never()).findById(id);
        assertEquals(first.getName().value(), second.getName().value());
        assertEquals(0, new BigDecimal("12.50").compareTo(second.getPrice().value()));
        assertEquals(10, second.getStock().value());
    }

    @Test
    void doesNotCacheAMissingProduct() {
        String missing = UUID.randomUUID().toString();

        assertTrue(repository.findById(new ProductId(missing)).isEmpty());

        assertNull(cached(missing), "not-found must not be memoized");
    }

    @Test
    void stockChangesEvictTheCachedProduct() throws Exception {
        Product product = savedProduct();
        repository.findById(product.getId());

        assertTrue(repository.tryReserveStock(product.getId(), 4));

        assertNull(cached(product.getId().value()));
        assertEquals(6, repository.findById(product.getId()).orElseThrow().getStock().value());
    }
}

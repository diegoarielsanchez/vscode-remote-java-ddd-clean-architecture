package com.das.infra.service.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.das.cleanddd.domain.catalog.entities.Product;
import com.das.cleanddd.domain.catalog.entities.ProductDescription;
import com.das.cleanddd.domain.catalog.entities.ProductId;
import com.das.cleanddd.domain.catalog.entities.ProductName;
import com.das.cleanddd.domain.catalog.entities.ProductPrice;
import com.das.cleanddd.domain.catalog.entities.ProductStock;
import com.das.cleanddd.domain.catalog.entities.ProductUnit;

/**
 * The repository's cache annotations evaluated by real Spring caching (production uses Redis).
 * Without a cache manager the SpEL in {@code @Cacheable}/{@code @CacheEvict} is never run, which is
 * how an expression calling {@code isEmpty()} on the unwrapped {@code Product} broke every lookup
 * in production while all other tests passed.
 */
@DataJpaTest
@Import({SQLProductRepository.class, SQLProductRepositoryCachingTest.CachingConfig.class})
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class SQLProductRepositoryCachingTest {

    @TestConfiguration
    @EnableCaching
    static class CachingConfig {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("productById");
        }
    }

    @Autowired private SQLProductRepository repository;
    @Autowired private CacheManager cacheManager;

    private Product savedProduct() throws Exception {
        Product product = Product.create(null, new ProductName("Amoxicillin 500mg"), new ProductDescription("Antibiotic"),
                new ProductPrice(new BigDecimal("12.50")), new ProductUnit("BOX"), new ProductStock(10));
        repository.save(product);
        return product;
    }

    @Test
    void findsAndCachesAnExistingProduct() throws Exception {
        Product product = savedProduct();

        assertTrue(repository.findById(product.getId()).isPresent());

        assertNotNull(cacheManager.getCache("productById").get(product.getId().value()), "found product is cached");
        assertEquals("Amoxicillin 500mg", repository.findById(product.getId()).orElseThrow().getName().value());
    }

    @Test
    void doesNotCacheAMissingProduct() {
        String missing = UUID.randomUUID().toString();

        assertTrue(repository.findById(new ProductId(missing)).isEmpty());

        assertNull(cacheManager.getCache("productById").get(missing), "not-found must not be memoized");
    }

    @Test
    void stockChangesEvictTheCachedProduct() throws Exception {
        Product product = savedProduct();
        repository.findById(product.getId());

        assertTrue(repository.tryReserveStock(product.getId(), 4));

        assertNull(cacheManager.getCache("productById").get(product.getId().value()));
        assertEquals(6, repository.findById(product.getId()).orElseThrow().getStock().value());
    }
}

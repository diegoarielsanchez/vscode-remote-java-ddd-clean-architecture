package com.das.visit.application.config;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Backs the {@code hcpActiveStatus}/{@code msrActiveStatus} caches used by
 * {@link com.das.infra.service.visit.HealthCareProfValidatorAdapter} and
 * {@link com.das.infra.service.visit.MedicalSalesRepValidatorAdapter} to avoid
 * a cross-service HTTP round trip on every visit/visit-plan creation.
 *
 * <p>A custom {@link CacheErrorHandler} is required, not optional: those
 * adapters already degrade gracefully (HTTP failure → local snapshot table)
 * when the HCP/MSR service is unreachable. Spring's default cache error
 * handling rethrows on a Redis connection failure, which would break that
 * existing resilience path the moment Redis itself has a blip. Logging and
 * swallowing instead makes Redis unavailability fail open — the annotated
 * method just runs as if uncached.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    private final RedisConnectionFactory redisConnectionFactory;

    public CacheConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    @Bean
    @Override
    public CacheManager cacheManager() {
        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig();
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(Map.of(
                        "hcpActiveStatus", defaults.entryTtl(Duration.ofSeconds(60)),
                        "msrActiveStatus", defaults.entryTtl(Duration.ofSeconds(60))
                ))
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed, falling through to source: cache={} key={} error={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed: cache={} key={} error={}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT failed: cache={} key={} error={}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed: cache={} error={}", cache.getName(), exception.getMessage());
            }
        };
    }
}

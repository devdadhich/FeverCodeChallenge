package com.fever.challenge.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache configuration for the search endpoint.
 *
 * Strategy:
 * - Cache up to 500 unique query results (different date range combinations).
 * - TTL of 60 seconds as a safety net -- even without explicit eviction,
 *   stale entries expire automatically.
 * - The sync service explicitly evicts all entries after each data refresh,
 *   so in practice, TTL only matters if sync fails repeatedly.
 *
 * Under 10k req/s, if most clients query similar date ranges, the vast
 * majority of requests are served from cache (sub-microsecond) without
 * touching the database.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("events");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .recordStats());
        return manager;
    }
}

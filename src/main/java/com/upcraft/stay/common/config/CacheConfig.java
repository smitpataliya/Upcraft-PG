package com.upcraft.stay.common.config;

import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures bounded short-TTL Caffeine caches for dashboard read aggregations.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String DASHBOARD_METRICS_CACHE = "dashboardMetrics";

    /**
     * Bounded cache manager with configurable short expiry to prevent stale financial views.
     *
     * @param appProperties typed reporting cache settings
     * @return configured Caffeine cache manager
     */
    @Bean
    public CacheManager cacheManager(AppProperties appProperties) {
        int ttlSeconds = Math.max(5, appProperties.getReports().getDashboardCacheSeconds());
        CaffeineCacheManager manager = new CaffeineCacheManager(DASHBOARD_METRICS_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS));
        return manager;
    }
}


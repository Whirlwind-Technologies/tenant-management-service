package com.nnipa.tenant.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis cache configuration for multi-tenant caching strategy.
 * Implements tier-based caching with different TTL for various organization types.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    /**
     * Custom cache configurations for different cache regions.
     * Each cache can have different TTL based on business requirements.
     */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> {
            Map<String, RedisCacheConfiguration> configMap = new HashMap<>();

            // Tenant cache - longer TTL for stable data
            configMap.put("tenants", RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofHours(1))
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                    .disableCachingNullValues());

            // Tenant settings - medium TTL
            configMap.put("tenant-settings", RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(30))
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                    .disableCachingNullValues());

            // Feature flags - short TTL for quick updates
            configMap.put("tenant-features", RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(5))
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                    .disableCachingNullValues());

            // Subscription cache - medium TTL
            configMap.put("subscriptions", RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(30))
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                    .disableCachingNullValues());

            // Organization type cache - long TTL for rarely changing data
            configMap.put("organization-types", RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofHours(24))
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                    .disableCachingNullValues());

            configMap.forEach((cacheName, config) -> {
                builder.withCacheConfiguration(cacheName, config);
                log.info("Configured cache: {} with TTL: {}", cacheName, config.getTtl());
            });
        };
    }

    /**
     * Custom key generator for complex cache keys.
     * Generates keys based on class name, method name, and parameters.
     */
    @Bean("customKeyGenerator")
    public KeyGenerator keyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getSimpleName()).append(":");
            sb.append(method.getName()).append(":");

            if (params.length > 0) {
                for (int i = 0; i < params.length; i++) {
                    if (params[i] != null) {
                        sb.append(params[i].toString());
                        if (i < params.length - 1) {
                            sb.append(",");
                        }
                    }
                }
            }

            String key = sb.toString();
            log.debug("Generated cache key: {}", key);
            return key;
        };
    }

    /**
     * Tenant-aware key generator that includes tenant context in cache keys.
     */
    @Bean("tenantKeyGenerator")
    public KeyGenerator tenantKeyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            // This will be enhanced to get tenant from context
            sb.append("tenant:").append("${tenantId}").append(":");
            sb.append(method.getName()).append(":");

            if (params.length > 0) {
                for (Object param : params) {
                    if (param != null) {
                        sb.append(param.hashCode()).append(":");
                    }
                }
            }

            return sb.toString();
        };
    }
}
package com.rahul.taskmanager.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {

        RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
                             .entryTtl(Duration.ofMinutes(10))
                             .disableCachingNullValues()
                             .serializeValuesWith(
                                     RedisSerializationContext.SerializationPair.fromSerializer(RedisSerializer.json())
              );

        RedisCacheWriter cacheWriter =  RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory);

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(cacheConfiguration)
                .build();
    }
}




/*
 * ============================================================
 * NOTE: Redis Cache Configuration — Approaches
 * ============================================================
 *
 * 1) MANUAL BEAN CONFIG (used above)
 * ------------------------------------------------------------
 * @Bean
 * public RedisCacheConfiguration cacheConfiguration() {
 *     return RedisCacheConfiguration.defaultCacheConfig()
 *             .entryTtl(Duration.ofMinutes(10))   // optional: default TTL
 *             .disableCachingNullValues()          // optional: skip null values
 *             .serializeValuesWith(
 *                     RedisSerializationContext.SerializationPair.fromSerializer(
 *                             RedisSerializer.json()
 *                     )
 *             );
 * }
 *
 * // The actual CacheManager bean that @EnableCaching looks for.
 * // This makes @Cacheable / @CachePut / @CacheEvict work with Redis.
 * @Bean
 * public RedisCacheManager cacheManager(
 *         RedisConnectionFactory redisConnectionFactory,
 *         RedisCacheConfiguration cacheConfiguration) {
 *
 *     RedisCacheWriter cacheWriter =
 *             RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory);
 *
 *     return RedisCacheManager.builder(cacheWriter)
 *             .cacheDefaults(cacheConfiguration)
 *             .build();
 * }
 *
 *
 * 2) ALTERNATIVE — application.yml (simpler, but applies GLOBALLY)
 * ------------------------------------------------------------
 * spring:
 *   cache:
 *     redis:
 *       time-to-live: 600000        # TTL in ms (10 min here)
 *       cache-null-values: false
 *       key-prefix: "taskmanager::"
 *       use-key-prefix: true
 *
 * -> Avoids writing the two @Bean methods above entirely.
 * -> LIMITATION: same TTL/settings apply to ALL caches in the app.
 *    No way to give "users" cache a different TTL than "tasks" cache
 *    using YAML alone.
 * -> NOTE: YAML alone also does NOT give you JSON serialization —
 *    Boot's default serializer here is still JDK binary serialization
 *    unless you customize it via a RedisCacheConfiguration bean.
 *
 *
 * 3) IF DIFFERENT CACHES NEED DIFFERENT TTLs — go back to Java
 * ------------------------------------------------------------
 * @Bean
 * public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
 *     // Build the FULL default config first — TTL + serializer + null handling — BEFORE deriving per-cache overrides from it.
 *     RedisCacheConfiguration cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
 *             .entryTtl(Duration.ofMinutes(10))
 *             .disableCachingNullValues()
 *             .serializeValuesWith(
 *                     RedisSerializationContext.SerializationPair.fromSerializer(
 *                             RedisSerializer.json()
 *                     )
 *             );
 *
 *     Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
 *     // .entryTtl(...) returns a NEW immutable copy — it still inherits the JSON serializer + null-value setting from cacheConfiguration.
 *     cacheConfigs.put("users", cacheConfiguration.entryTtl(Duration.ofMinutes(5)));
 *     cacheConfigs.put("tasks", cacheConfiguration.entryTtl(Duration.ofHours(1)));
 *
 *     return RedisCacheManager.builder(RedisCacheWriter.nonLockingRedisCacheWriter(factory))
 *             .cacheDefaults(cacheConfiguration)
 *             .withInitialCacheConfigurations(cacheConfigs)
 *             .build();
 * }
 *
 * ⚠ COMMON MISTAKE: forgetting .serializeValuesWith(...) when building
 * cacheConfiguration here. If skipped, RedisCacheConfiguration falls back to
 * JdkSerializationRedisSerializer (binary Java serialization), NOT JSON —
 * cached objects must then implement Serializable, and Redis values
 * become unreadable binary blobs instead of JSON.
 *
 *
 * SUMMARY
 * ------------------------------------------------------------
 * | Approach              | Use when                                  |
 * |------------------------|--------------------------------------------|
 * | application.yml only   | Same TTL/rules for all caches (simplest)   |
 * | Java @Bean methods     | Per-cache TTLs, custom serializers, etc.   |
 * ============================================================
 */
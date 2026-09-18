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

    /**
     * Defines the serialization + TTL settings for Redis cache entries.
     * Kept as a separate bean so it can be reused/injected elsewhere if needed.
     */
    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) // optional: set a default TTL
                .disableCachingNullValues()        // optional: avoid caching nulls
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                RedisSerializer.json()
                        )
                );
    }

    /**
     * The actual CacheManager bean that Spring's @EnableCaching looks for.
     * This is what makes @Cacheable / @CachePut / @CacheEvict work with Redis.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory redisConnectionFactory, RedisCacheConfiguration cacheConfiguration) {

        RedisCacheWriter cacheWriter =
                RedisCacheWriter.nonLockingRedisCacheWriter(redisConnectionFactory);

        return RedisCacheManager.builder(cacheWriter)
                .cacheDefaults(cacheConfiguration)
                .build();
    }
}
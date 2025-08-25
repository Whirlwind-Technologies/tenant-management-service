package com.nnipa.tenant.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.time.Duration;

/**
 * Redis configuration for distributed caching and session management.
 * Optimized for multi-tenant access patterns with connection pooling.
 */
@Slf4j
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.timeout:2000ms}")
    private Duration timeout;

    /**
     * Configure Redis connection factory with connection pooling.
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        if (!redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }

        // Configure connection pooling with proper generic type
        GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(2);
        // Use the new method instead of deprecated setMaxWaitMillis
        poolConfig.setMaxWait(Duration.ofSeconds(2));
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setMinEvictableIdleDuration(Duration.ofMinutes(1));

        // Configure Lettuce client
        ClientOptions clientOptions = ClientOptions.builder()
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build();

        ClientResources clientResources = DefaultClientResources.builder()
                .ioThreadPoolSize(4)
                .computationThreadPoolSize(4)
                .build();

        LettucePoolingClientConfiguration lettucePoolConfig = LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .clientOptions(clientOptions)
                .clientResources(clientResources)
                .commandTimeout(timeout)
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, lettucePoolConfig);
        factory.setValidateConnection(true);

        log.info("Redis connection factory configured - Host: {}, Port: {}", redisHost, redisPort);

        return factory;
    }

    /**
     * Configure RedisTemplate for object serialization.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Configure Jackson serializer with type information
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer jackson2JsonRedisSerializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);

        // Use String serializer for keys
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // Set serializers
        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();

        log.info("RedisTemplate configured with Jackson serialization");

        return template;
    }

    /**
     * Separate RedisTemplate for tenant-specific operations.
     */
    @Bean("tenantStringRedisTemplate")
    public RedisTemplate<String, String> tenantStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringRedisSerializer);
        template.setValueSerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);
        template.setHashValueSerializer(stringRedisSerializer);

        template.afterPropertiesSet();

        return template;
    }
}
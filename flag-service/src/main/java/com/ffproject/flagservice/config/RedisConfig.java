package com.ffproject.flagservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for the Flag Service.
 *
 * <p>This configuration provides a {@link RedisTemplate} configured for
 * String serialization of both keys and values, suitable for publishing
 * JSON messages via Redis Pub/Sub.</p>
 *
 * <h3>Connection Source</h3>
 * <p>The connection details come from {@code spring.data.redis.url}, backed by
 * the {@code REDIS_URL} environment variable. The same variable is used for
 * local testing and for the cloud deployment, so there is nothing
 * profile-specific to keep in sync:</p>
 * <ul>
 *   <li><b>Upstash Redis:</b> a {@code rediss://user:password@host:port} URL.
 *       Spring Boot enables TLS automatically because the scheme is
 *       {@code rediss}, so no separate SSL property is required.</li>
 *
 *   <li><b>Local development:</b> the property defaults to
 *       {@code redis://localhost:6379}, which is where the Redis container from
 *       {@code docker-compose.yml} listens. Inside the compose network the value
 *       is overridden with {@code redis://redis:6379} so the container is reached
 *       by service name.</li>
 * </ul>
 *
 * <p>The {@code spring.data.redis.*} prefix is required: the older
 * {@code spring.redis.*} names are deprecated and Spring Boot 3.2 does not bind
 * them, which silently leaves the client pointed at {@code localhost:6379}.</p>
 *
 * <p>The {@link RedisConnectionFactory} bean is provided automatically by
 * Spring Boot's auto-configuration based on that property.</p>
 */
@Configuration
public class RedisConfig {

    /**
     * Creates a {@link RedisTemplate} configured for String serialization.
     *
     * <p>Both keys and values are serialized using {@link StringRedisSerializer},
     * which is appropriate for storing JSON strings and publishing messages
     * to Redis Pub/Sub channels.</p>
     *
     * @param connectionFactory the Redis connection factory (auto-configured
     *                          by Spring Boot from {@code spring.data.redis.url})
     * @return a configured {@link RedisTemplate} with String serializers
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use StringRedisSerializer for both keys and values
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
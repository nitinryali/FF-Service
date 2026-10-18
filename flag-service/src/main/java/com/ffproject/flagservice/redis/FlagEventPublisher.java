package com.ffproject.flagservice.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes flag state changes to the Redis Pub/Sub channel consumed by consumer
 * services.
 *
 * <p>Each message is a JSON string of the form
 * {@code {"name":…,"value":…,"event":"created"|"updated"|"deleted"}}, published on
 * the {@code flag-updates} channel. {@code value} is {@code null} for deletion
 * events.</p>
 *
 * <p>Publishing is fire-and-forget: the flag has already been persisted by the
 * time this runs, so a Redis outage must never fail the caller's request. Every
 * failure is logged at WARN and swallowed (Requirement 3.1).</p>
 */
@Component
public class FlagEventPublisher {

    /** Redis Pub/Sub channel that carries flag change notifications. */
    static final String CHANNEL = "flag-updates";

    private static final String FIELD_NAME = "name";
    private static final String FIELD_VALUE = "value";
    private static final String FIELD_EVENT = "event";

    private static final Logger log = LoggerFactory.getLogger(FlagEventPublisher.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public FlagEventPublisher(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Publishes a flag change event.
     *
     * @param name  the flag name
     * @param value the new boolean value, or {@code null} for a deletion
     * @param event one of {@code created}, {@code updated}, or {@code deleted}
     */
    public void publish(String name, Boolean value, String event) {
        try {
            // LinkedHashMap (not Map.of) — Map.of rejects null values, and deletions carry null.
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put(FIELD_NAME, name);
            payload.put(FIELD_VALUE, value);
            payload.put(FIELD_EVENT, event);

            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(CHANNEL, json);
        } catch (Exception ex) {
            log.warn("Redis publish failed for flag {}: {}", name, ex.getMessage(), ex);
        }
    }
}

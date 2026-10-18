package com.ffproject.flagservice.config;

import com.mongodb.MongoClientSettings;
import org.springframework.boot.autoconfigure.mongo.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

import java.util.concurrent.TimeUnit;

/**
 * MongoDB configuration.
 *
 * <p>{@link EnableMongoAuditing} activates Spring Data MongoDB auditing, which automatically
 * populates the {@code @CreatedDate} and {@code @LastModifiedDate} fields on the
 * {@code Flag} document when it is persisted or updated. This supports Requirement 6.1,
 * which requires every Flag record stored in the {@code flags} collection to carry
 * {@code createdAt} and {@code updatedAt} timestamps.</p>
 *
 * <p>This lives in its own {@code @Configuration} class rather than on
 * {@code FlagServiceApplication} so that {@code @WebMvcTest} slices — which load the
 * application class but skip regular {@code @Configuration} classes — do not require a
 * {@code MongoMappingContext} bean they have no way to provide.</p>
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    /**
     * How long the driver waits for a reachable MongoDB server before giving up
     * (Requirements 6.2, 6.3).
     */
    private static final long SERVER_SELECTION_TIMEOUT_SECONDS = 5;

    /**
     * Caps the driver's server-selection wait so that an unreachable MongoDB
     * fails fast at startup instead of hanging for the 30-second default.
     *
     * <p>This is applied to the {@link MongoClientSettings.Builder} rather than
     * appended to the connection URI as a {@code ?serverSelectionTimeoutMS=5000}
     * query parameter. Appending is unsafe: a standard MongoDB Atlas URI already
     * carries a query string (for example
     * {@code ?retryWrites=true&w=majority}), so the appended parameter fuses onto
     * the last one and turns {@code w} into the invalid tag
     * {@code majority?serverSelectionTimeoutMS=5000} — silently breaking writes
     * while dropping the timeout entirely. Setting it here works in every
     * environment and leaves the configured URI untouched.</p>
     *
     * @return a customizer applied to the auto-configured Mongo client settings
     */
    @Bean
    public MongoClientSettingsBuilderCustomizer mongoServerSelectionTimeoutCustomizer() {
        return builder -> builder.applyToClusterSettings(cluster -> cluster
                .serverSelectionTimeout(SERVER_SELECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS));
    }
}

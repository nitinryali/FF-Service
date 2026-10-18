package com.ffproject.flagservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Entry point for the Feature Flag Service Spring Boot application.
 *
 * <p>The {@code @SpringBootApplication} annotation enables auto-configuration, component
 * scanning, and configuration registration for the {@code com.ffproject.flagservice} package
 * and its sub-packages ({@code config}, {@code auth}, {@code flag}, {@code redis},
 * {@code exception}).
 *
 * <p>MongoDB auditing is enabled by
 * {@link com.ffproject.flagservice.config.MongoConfig}, which populates the
 * {@code @CreatedDate} and {@code @LastModifiedDate} fields on the {@code Flag} document
 * (Requirement 6.1).
 *
 * <p>{@link UserDetailsServiceAutoConfiguration} is excluded because the service authenticates
 * exclusively via signed JWTs (see {@code com.ffproject.flagservice.auth.JwtFilter}) and never
 * uses a {@code UserDetailsService}. Without the exclusion, Spring Boot would create an unused
 * in-memory user and log a generated password at every startup.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class FlagServiceApplication {

    /**
     * Boots the Spring Boot application.
     *
     * @param args command-line arguments forwarded to the Spring Boot startup process
     */
    public static void main(String[] args) {
        SpringApplication.run(FlagServiceApplication.class, args);
    }
}

package com.ffproject.flagservice.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the initial platform administrator into the {@code users} collection.
 *
 * <p>Before this change the service compared logins against a single
 * {@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD} pair. Accounts now live in
 * MongoDB, so on first boot — or whenever the configured admin username is
 * absent — this runner creates that account with a BCrypt-hashed password.
 * The environment variables keep working as bootstrap credentials only.</p>
 *
 * <p>Seeding is idempotent: once the username exists, nothing is changed.</p>
 */
@Component
public class UserBootstrap implements ApplicationRunner {

    /** Reserved tenant id for platform operators; they act across tenants. */
    public static final String PLATFORM_TENANT_ID = "platform";

    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public UserBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }

        AppUser admin = new AppUser();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setTenantId(PLATFORM_TENANT_ID);
        admin.setRole(Role.PLATFORM_ADMIN);

        userRepository.save(admin);
        log.info("Seeded platform admin '{}' from bootstrap configuration", adminUsername);
    }
}

package com.ffproject.flagservice.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Authentication component embedded in the Flag Service.
 *
 * <p>Validates the single configured admin credential pair and issues signed
 * HS256 JWTs. The signing secret is loaded from environment configuration
 * ({@code jwt.secret}, backed by the {@code JWT_SECRET} environment variable)
 * and is never embedded in source code (Requirement 2.5).</p>
 *
 * <p>All credential failures — wrong length, wrong value, or {@code null} —
 * raise the same {@link BadCredentialsException} with an identical generic
 * message so that no information about <em>which</em> field was incorrect is
 * leaked to the caller (Requirements 2.1, 2.6).</p>
 *
 * <p>The configured token lifetime is validated at startup against the
 * requirement's 60–86400 second range, so an out-of-range
 * {@code JWT_EXPIRY_SECONDS} fails fast instead of silently issuing tokens
 * with an unusable lifetime (Requirement 2.1).</p>
 */
@Service
public class AuthService {

    /** Inclusive bounds for the username field, per Requirement 2.1. */
    private static final int USERNAME_MIN_LENGTH = 1;
    private static final int USERNAME_MAX_LENGTH = 64;

    /** Inclusive bounds for the password field, per Requirement 2.1. */
    private static final int PASSWORD_MIN_LENGTH = 1;
    private static final int PASSWORD_MAX_LENGTH = 128;

    /** Single generic failure message, identical for every failure mode. */
    private static final String AUTH_FAILURE_MESSAGE = "Authentication failed";

    /** Inclusive bounds for the configurable token lifetime (Requirement 2.1). */
    private static final long EXPIRY_MIN_SECONDS = 60;
    private static final long EXPIRY_MAX_SECONDS = 86_400;

    private final String adminUsername;
    private final String adminPassword;
    private final SecretKey signingKey;
    private final long jwtExpirySeconds;

    public AuthService(
            @Value("${app.admin.username}") String adminUsername,
            @Value("${app.admin.password}") String adminPassword,
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiry-seconds}") long jwtExpirySeconds) {
        if (jwtExpirySeconds < EXPIRY_MIN_SECONDS || jwtExpirySeconds > EXPIRY_MAX_SECONDS) {
            throw new IllegalArgumentException(
                    "jwt.expiry-seconds must be between " + EXPIRY_MIN_SECONDS + " and " + EXPIRY_MAX_SECONDS
                            + " seconds, but was " + jwtExpirySeconds);
        }

        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirySeconds = jwtExpirySeconds;
    }

    /**
     * Validates the supplied credentials against the configured admin account.
     *
     * @param username the submitted username, must be 1–64 characters
     * @param password the submitted password, must be 1–128 characters
     * @throws BadCredentialsException if the credentials are missing, malformed,
     *                                 or do not match the configured admin account
     */
    public void authenticate(String username, String password) {
        boolean lengthValid = hasValidLength(username, USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH)
                && hasValidLength(password, PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH);
        if (!lengthValid) {
            throw new BadCredentialsException(AUTH_FAILURE_MESSAGE);
        }

        // Single-admin demo: constant-time comparison is not required (see design).
        if (!adminUsername.equals(username) || !adminPassword.equals(password)) {
            throw new BadCredentialsException(AUTH_FAILURE_MESSAGE);
        }
    }

    /**
     * Issues a signed JWT for the given subject.
     *
     * <p>The token carries the standard {@code sub}, {@code iat}, and {@code exp}
     * claims, with the lifetime set to the configured {@code jwt.expiry-seconds}
     * (Requirement 2.1).</p>
     *
     * @param username the subject to embed in the token
     * @return a compact, signed JWT string
     */
    public String generateToken(String username) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(jwtExpirySeconds);

        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private static boolean hasValidLength(String value, int min, int max) {
        return value != null && value.length() >= min && value.length() <= max;
    }
}

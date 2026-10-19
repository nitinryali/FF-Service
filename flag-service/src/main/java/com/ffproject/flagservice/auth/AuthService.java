package com.ffproject.flagservice.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Authentication component of the Flag Service.
 *
 * <p>Credentials are read from the {@code users} collection rather than from a
 * single configured admin account. Passwords are stored as BCrypt hashes, so
 * the plaintext is never persisted and a database leak does not expose usable
 * credentials.</p>
 *
 * <p>Issued tokens are signed HS256 JWTs carrying the standard {@code sub},
 * {@code iat}, and {@code exp} claims plus the {@code tenantId} and
 * {@code role} claims the rest of the service authorizes against. The signing
 * secret is loaded from environment configuration ({@code jwt.secret}, backed
 * by {@code JWT_SECRET}) and is never embedded in source code.</p>
 *
 * <p>All credential failures raise the same {@link BadCredentialsException}
 * with an identical generic message, so no information about <em>which</em>
 * field was wrong is leaked.</p>
 */
@Service
public class AuthService {

    private static final int USERNAME_MIN_LENGTH = 1;
    private static final int USERNAME_MAX_LENGTH = 64;
    private static final int PASSWORD_MIN_LENGTH = 1;
    private static final int PASSWORD_MAX_LENGTH = 128;

    private static final String AUTH_FAILURE_MESSAGE = "Authentication failed";

    private static final long EXPIRY_MIN_SECONDS = 60;
    private static final long EXPIRY_MAX_SECONDS = 86_400;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecretKey signingKey;
    private final long jwtExpirySeconds;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiry-seconds}") long jwtExpirySeconds) {
        if (jwtExpirySeconds < EXPIRY_MIN_SECONDS || jwtExpirySeconds > EXPIRY_MAX_SECONDS) {
            throw new IllegalArgumentException(
                    "jwt.expiry-seconds must be between " + EXPIRY_MIN_SECONDS + " and " + EXPIRY_MAX_SECONDS
                            + " seconds, but was " + jwtExpirySeconds);
        }

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirySeconds = jwtExpirySeconds;
    }

    /**
     * Validates the submitted credentials against the {@code users} collection.
     *
     * @param username the submitted username, must be 1–64 characters
     * @param password the submitted password, must be 1–128 characters
     * @return the authenticated account
     * @throws BadCredentialsException if the credentials are missing, malformed,
     *                                 or do not match a stored account
     */
    public AppUser authenticate(String username, String password) {
        boolean lengthValid = hasValidLength(username, USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH)
                && hasValidLength(password, PASSWORD_MIN_LENGTH, PASSWORD_MAX_LENGTH);
        if (!lengthValid) {
            throw new BadCredentialsException(AUTH_FAILURE_MESSAGE);
        }

        AppUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException(AUTH_FAILURE_MESSAGE));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException(AUTH_FAILURE_MESSAGE);
        }

        return user;
    }

    /**
     * Issues a signed JWT for the given account.
     *
     * @param user the authenticated account
     * @return a compact, signed JWT string
     */
    public String generateToken(AppUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(jwtExpirySeconds);

        return Jwts.builder()
                .subject(user.getUsername())
                .claim("tenantId", user.getTenantId())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private static boolean hasValidLength(String value, int min, int max) {
        return value != null && value.length() >= min && value.length() <= max;
    }
}

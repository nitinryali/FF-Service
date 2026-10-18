package com.ffproject.flagservice.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Stateless JWT verification filter.
 *
 * <p>Runs once per request and rejects any request to a protected endpoint
 * whose {@code Authorization} header is missing, is not a Bearer token, or
 * carries a token that is expired, malformed, or tampered with. All rejections
 * are written as JSON directly to the response, so no downstream controller is
 * ever invoked and no flag data can be modified (Requirements 1.6, 2.2, 2.3,
 * 2.4, 2.7).</p>
 *
 * <p>The following paths are skipped entirely: {@code /auth/**},
 * {@code /swagger-ui/**}, {@code /swagger-ui.html}, and
 * {@code /v3/api-docs/**} (plus the bare {@code /v3/api-docs} path, which some
 * path matchers treat as distinct from the {@code /**} variant).</p>
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    /** Required prefix for the Authorization header value. */
    static final String BEARER_PREFIX = "Bearer ";

    /** Paths that never require a JWT. */
    private static final List<String> PUBLIC_PATH_PATTERNS = List.of(
            "/auth/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/v3/api-docs/**",
            "/v3/api-docs");

    private final SecretKey signingKey;
    private final ObjectMapper objectMapper;
    private final PathMatcher pathMatcher = new AntPathMatcher();

    public JwtFilter(@Value("${jwt.secret}") String jwtSecret, ObjectMapper objectMapper) {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.objectMapper = objectMapper;
    }

    /**
     * Skips JWT verification for the public paths listed above.
     *
     * @param request the incoming request
     * @return {@code true} when the request must not be filtered
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return PUBLIC_PATH_PATTERNS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            writeError(response, "Authentication required");
            return;
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(claims.getSubject(), null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, "Token has expired");
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, "Invalid token");
        }
    }

    private void writeError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }
}

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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Stateless JWT verification filter.
 *
 * <p>Runs once per request and rejects any request to a protected endpoint
 * whose {@code Authorization} header is missing, is not a Bearer token, or
 * carries a token that is expired, malformed, or tampered with. All rejections
 * are written as JSON directly to the response, so no downstream controller is
 * ever invoked.</p>
 *
 * <p>On success the filter places an {@link AuthenticatedUser} principal — the
 * user's name, tenant, and role from the token claims — and the matching
 * {@code ROLE_*} authority into the {@code SecurityContext}, where the tenant
 * interceptor and method security read them from.</p>
 *
 * <p>The following paths are skipped entirely: {@code /auth/**},
 * {@code /swagger-ui/**}, {@code /swagger-ui.html}, and
 * {@code /v3/api-docs/**}. CORS preflight requests are skipped too, so an
 * {@code OPTIONS} probe is answered with CORS headers instead of a 401.</p>
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    /** Required prefix for the Authorization header value. */
    static final String BEARER_PREFIX = "Bearer ";

    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_ROLE = "role";

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

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Let the CORS filter answer preflight requests; they carry no token.
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
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

            String username = claims.getSubject();
            String tenantId = claims.get(CLAIM_TENANT_ID, String.class);
            Role role = Role.valueOf(claims.get(CLAIM_ROLE, String.class));

            AuthenticatedUser principal = new AuthenticatedUser(username, tenantId, role);
            List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role.authority()));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            SecurityContextHolder.clearContext();
            writeError(response, "Token has expired");
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
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

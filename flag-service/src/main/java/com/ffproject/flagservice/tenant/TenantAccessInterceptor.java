package com.ffproject.flagservice.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ffproject.flagservice.auth.AuthenticatedUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Enforces tenant scoping on every flag route.
 *
 * <p>Rules:</p>
 * <ol>
 *   <li>The {@code X-Tenant-Id} header is mandatory — a missing or blank header
 *       is a {@code 400}, never an implicit default.</li>
 *   <li>A tenant user may only use the tenant id carried in their JWT; any
 *       other value is a {@code 403}, so one tenant cannot reach another
 *       tenant's flags.</li>
 *   <li>A platform administrator may pass any tenant id (support/operations),
 *       but the header is still required.</li>
 * </ol>
 *
 * <p>Controllers read the same header back, trusting that it has already been
 * validated here.</p>
 */
@Component
public class TenantAccessInterceptor implements HandlerInterceptor {

    /** Required request header naming the tenant. */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    private final ObjectMapper objectMapper;

    public TenantAccessInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Authentication required");
            return false;
        }

        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            writeError(response, HttpStatus.BAD_REQUEST, "Tenant header required (X-Tenant-Id)");
            return false;
        }
        tenantId = tenantId.trim();

        if (!user.isPlatformAdmin() && !tenantId.equals(user.tenantId())) {
            writeError(response, HttpStatus.FORBIDDEN, "Tenant access denied");
            return false;
        }

        return true;
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }
}

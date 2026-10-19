package com.ffproject.flagservice.auth;

/**
 * The principal extracted from a verified JWT and placed in the
 * {@code SecurityContext}.
 *
 * @param username the subject that logged in
 * @param tenantId the tenant the user belongs to
 * @param role     the user's role
 */
public record AuthenticatedUser(String username, String tenantId, Role role) {

    /**
     * @return {@code true} when this user may operate across tenants
     */
    public boolean isPlatformAdmin() {
        return role == Role.PLATFORM_ADMIN;
    }
}

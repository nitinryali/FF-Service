package com.ffproject.flagservice.auth;

/**
 * Authorization roles carried in a signed JWT.
 *
 * <ul>
 *   <li>{@link #PLATFORM_ADMIN} — operates the platform: creates/deletes tenants
 *       and users, and may read and manage flags for any tenant.</li>
 *   <li>{@link #TENANT_USER} — belongs to exactly one tenant and may only reach
 *       that tenant's flags.</li>
 * </ul>
 */
public enum Role {

    PLATFORM_ADMIN,

    TENANT_USER;

    /** Spring Security authority name, i.e. {@code ROLE_PLATFORM_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }
}

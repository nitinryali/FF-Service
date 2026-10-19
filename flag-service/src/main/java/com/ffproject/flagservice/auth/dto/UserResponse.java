package com.ffproject.flagservice.auth.dto;

import com.ffproject.flagservice.auth.AppUser;
import com.ffproject.flagservice.auth.Role;
import lombok.Getter;

import java.time.Instant;

/**
 * Outbound representation of an {@link AppUser}. Never carries the hash.
 */
@Getter
public class UserResponse {

    private final String id;
    private final String username;
    private final String tenantId;
    private final Role role;
    private final Instant createdAt;

    private UserResponse(String id, String username, String tenantId, Role role, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.tenantId = tenantId;
        this.role = role;
        this.createdAt = createdAt;
    }

    /**
     * Maps a persisted {@link AppUser} onto its API representation.
     *
     * @param user the document to map
     * @return the corresponding response payload
     */
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getTenantId(), user.getRole(), user.getCreatedAt());
    }
}

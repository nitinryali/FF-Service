package com.ffproject.flagservice.tenant.dto;

import com.ffproject.flagservice.tenant.Tenant;
import lombok.Getter;

import java.time.Instant;

/**
 * Outbound representation of a {@link Tenant}.
 */
@Getter
public class TenantResponse {

    private final String id;
    private final String name;
    private final Instant createdAt;
    private final Instant updatedAt;

    private TenantResponse(String id, String name, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Maps a persisted {@link Tenant} onto its API representation.
     *
     * @param tenant the document to map
     * @return the corresponding response payload
     */
    public static TenantResponse from(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getName(), tenant.getCreatedAt(), tenant.getUpdatedAt());
    }
}

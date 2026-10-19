package com.ffproject.flagservice.flag.dto;

import com.ffproject.flagservice.flag.Flag;
import lombok.Getter;

import java.time.Instant;

/**
 * Outbound representation of a {@link Flag}.
 *
 * <p>Decouples the API contract from the MongoDB document so that persistence
 * concerns (the {@code @Id} field, auditing annotations) never leak into
 * responses. Serialised as
 * {@code {"id":…,"tenantId":…,"name":…,"value":…,"version":…,"createdAt":…,"updatedAt":…}}.</p>
 */
@Getter
public class FlagResponse {

    private final String id;
    private final String tenantId;
    private final String name;
    private final Boolean value;
    private final Long version;
    private final Instant createdAt;
    private final Instant updatedAt;

    private FlagResponse(String id, String tenantId, String name, Boolean value, Long version,
                         Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.value = value;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Maps a persisted {@link Flag} onto its API representation.
     *
     * @param flag the document to map, must not be {@code null}
     * @return the corresponding response payload
     */
    public static FlagResponse from(Flag flag) {
        return new FlagResponse(
                flag.getId(),
                flag.getTenantId(),
                flag.getName(),
                flag.getValue(),
                flag.getVersion(),
                flag.getCreatedAt(),
                flag.getUpdatedAt());
    }
}

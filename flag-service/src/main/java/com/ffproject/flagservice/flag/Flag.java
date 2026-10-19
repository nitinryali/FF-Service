package com.ffproject.flagservice.flag;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document model for the Feature Flag entity.
 *
 * <p>Flags are partitioned by {@code tenantId}: a name is unique only within a
 * tenant, never globally. The partial compound index enforces that and ignores
 * soft-deleted documents, so a name can be reused after deletion.</p>
 *
 * @see FlagRepository
 */
@Data
@Document(collection = "flags")
@CompoundIndex(
        name = "tenant_name_unique",
        def = "{'tenantId': 1, 'name': 1}",
        unique = true,
        partialFilter = "{\"deletedAt\": null}")
public class Flag {

    @Id
    private String id;

    /** Owning tenant. Every query is scoped by this field. */
    @Indexed
    private String tenantId;

    @Size(max = 255)
    private String name;

    private Boolean value;

    /** Optimistic-locking / event-ordering version, maintained by Spring Data. */
    @Version
    private Long version;

    /** Set instead of removing the document, so events and audit stay coherent. */
    private Instant deletedAt;

    private String createdBy;

    private String updatedBy;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

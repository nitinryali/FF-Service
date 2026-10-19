package com.ffproject.flagservice.tenant;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document model for a Tenant.
 *
 * <p>A tenant is the isolation boundary for flags and users: a flag name is
 * unique within a tenant, and a tenant's users can never reach another tenant's
 * flags.</p>
 */
@Data
@Document(collection = "tenants")
public class Tenant {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

package com.ffproject.flagservice.flag;

import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document model for the Feature Flag entity.
 *
 * <p>Represents a feature flag with a unique name, boolean value, and timestamps
 * for tracking creation and modification dates.</p>
 *
 * @see FlagRepository
 */
@Data
@Document(collection = "flags")
public class Flag {

    @Id
    private String id;

    @Indexed(unique = true)
    @Size(max = 255)
    private String name;

    private Boolean value;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
package com.ffproject.flagservice.auth;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document model for a login account.
 *
 * <p>Replaces the former single hard-coded admin credential: credentials now
 * live in the {@code users} collection, passwords are stored only as BCrypt
 * hashes, and each user is bound to a tenant and a {@link Role}.</p>
 */
@Data
@Document(collection = "users")
public class AppUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    /** BCrypt hash — the plaintext password is never stored. */
    private String passwordHash;

    /** The tenant this user may act on. */
    @Indexed
    private String tenantId;

    private Role role;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}

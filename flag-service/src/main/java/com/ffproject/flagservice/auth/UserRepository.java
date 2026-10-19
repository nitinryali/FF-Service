package com.ffproject.flagservice.auth;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for the {@code users} collection.
 *
 * @see AppUser
 */
@Repository
public interface UserRepository extends MongoRepository<AppUser, String> {

    /**
     * Looks an account up by username.
     *
     * @param username the login name
     * @return the account if present
     */
    Optional<AppUser> findByUsername(String username);

    /**
     * Reports whether a username is already taken.
     *
     * @param username the login name
     * @return {@code true} if the username is in use
     */
    boolean existsByUsername(String username);

    /**
     * Removes every account bound to a tenant. Used when a tenant is deleted.
     *
     * @param tenantId the tenant whose users should be removed
     */
    long deleteByTenantId(String tenantId);
}

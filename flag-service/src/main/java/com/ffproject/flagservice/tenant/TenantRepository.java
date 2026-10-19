package com.ffproject.flagservice.tenant;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for the Tenant collection.
 *
 * @see Tenant
 */
@Repository
public interface TenantRepository extends MongoRepository<Tenant, String> {

    /**
     * Looks a tenant up by its unique name.
     *
     * @param name the tenant name
     * @return the tenant if present
     */
    Optional<Tenant> findByName(String name);

    /**
     * Reports whether a tenant name is already taken.
     *
     * @param name the tenant name
     * @return {@code true} if the name is in use
     */
    boolean existsByName(String name);
}

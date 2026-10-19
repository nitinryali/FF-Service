package com.ffproject.flagservice.flag;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for the Flag document collection.
 *
 * <p>Every lookup is scoped by {@code tenantId} so that a caller can never
 * reach another tenant's flag. Soft-deleted documents ({@code deletedAt}
 * non-null) are excluded from all reads.</p>
 *
 * @see Flag
 */
@Repository
public interface FlagRepository extends MongoRepository<Flag, String> {

    /**
     * Finds a live flag by tenant and unique-within-tenant name.
     *
     * @param tenantId the owning tenant
     * @param name     the flag name
     * @return the flag if it exists and is not deleted
     */
    Optional<Flag> findByTenantIdAndNameAndDeletedAtIsNull(String tenantId, String name);

    /**
     * Finds a live flag by id, but only if it belongs to the given tenant.
     *
     * <p>Scoping by tenant is what prevents an id from one tenant being used to
     * mutate another tenant's flag (IDOR).</p>
     *
     * @param id       the flag id
     * @param tenantId the expected owning tenant
     * @return the flag if it exists, belongs to {@code tenantId}, and is not deleted
     */
    Optional<Flag> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);

    /**
     * Lists every live flag in a tenant, oldest first.
     *
     * @param tenantId the owning tenant
     * @return the tenant's live flags
     */
    List<Flag> findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(String tenantId);

    /**
     * Permanently removes every flag in a tenant. Used when a tenant is deleted.
     *
     * @param tenantId the tenant whose flags should be removed
     */
    long deleteByTenantId(String tenantId);
}

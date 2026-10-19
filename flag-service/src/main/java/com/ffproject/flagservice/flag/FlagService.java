package com.ffproject.flagservice.flag;

import com.ffproject.flagservice.auth.AuthenticatedUser;
import com.ffproject.flagservice.exception.FlagAlreadyExistsException;
import com.ffproject.flagservice.exception.FlagNotFoundException;
import com.ffproject.flagservice.exception.ServiceUnavailableException;
import com.ffproject.flagservice.exception.TenantNotFoundException;
import com.ffproject.flagservice.redis.FlagEventPublisher;
import com.ffproject.flagservice.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Business logic for flag management.
 *
 * <p>Everything here is tenant-scoped. A flag is looked up by
 * {@code (tenantId, id)} or {@code (tenantId, name)}, so an id or name from one
 * tenant can never reach another tenant's data.</p>
 *
 * <p>Owns the translation from persistence and messaging failures into the
 * domain exceptions the API layer understands:</p>
 * <ul>
 *   <li>duplicate name within a tenant → {@link FlagAlreadyExistsException} (409)</li>
 *   <li>missing flag → {@link FlagNotFoundException} (404)</li>
 *   <li>missing tenant on create → {@link TenantNotFoundException} (404)</li>
 *   <li>any other Mongo outage → {@link ServiceUnavailableException} (503)</li>
 * </ul>
 *
 * <p>Redis publication only ever happens <em>after</em> a successful write, so a
 * failed write can never produce a phantom flag-change event.</p>
 */
@Service
public class FlagService {

    private static final Logger log = LoggerFactory.getLogger(FlagService.class);

    private static final String EVENT_CREATED = "created";
    private static final String EVENT_UPDATED = "updated";
    private static final String EVENT_DELETED = "deleted";

    private static final String SYSTEM_PRINCIPAL = "system";

    private final FlagRepository flagRepository;
    private final TenantRepository tenantRepository;
    private final FlagEventPublisher flagEventPublisher;

    public FlagService(FlagRepository flagRepository,
                       TenantRepository tenantRepository,
                       FlagEventPublisher flagEventPublisher) {
        this.flagRepository = flagRepository;
        this.tenantRepository = tenantRepository;
        this.flagEventPublisher = flagEventPublisher;
    }

    /**
     * Creates and persists a new flag inside a tenant, then announces it.
     *
     * @param tenantId the owning tenant
     * @param name     the name, unique within the tenant
     * @param value    the initial boolean value
     * @return the persisted flag, including id, version, and timestamps
     * @throws TenantNotFoundException if the tenant does not exist
     * @throws FlagAlreadyExistsException if the name is already used in this tenant
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public Flag createFlag(String tenantId, String name, Boolean value) {
        if (!tenantExists(tenantId)) {
            throw new TenantNotFoundException();
        }

        Flag flag = new Flag();
        flag.setTenantId(tenantId);
        flag.setName(name);
        flag.setValue(value);

        String principal = currentPrincipal();
        flag.setCreatedBy(principal);
        flag.setUpdatedBy(principal);

        Flag saved = saveNewFlag(flag);

        publishSafely(saved, value, EVENT_CREATED);
        return saved;
    }

    /**
     * Returns every live flag in a tenant, or an empty list when none exist.
     *
     * @param tenantId the owning tenant
     * @return the tenant's flags
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public List<Flag> getAllFlags(String tenantId) {
        try {
            return flagRepository.findByTenantIdAndDeletedAtIsNullOrderByCreatedAtAsc(tenantId);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Looks up a single live flag by name within a tenant.
     *
     * @param tenantId the owning tenant
     * @param name     the flag name
     * @return the matching flag
     * @throws FlagNotFoundException if the tenant has no live flag with that name
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public Flag getFlagByName(String tenantId, String name) {
        try {
            return flagRepository.findByTenantIdAndNameAndDeletedAtIsNull(tenantId, name)
                    .orElseThrow(FlagNotFoundException::new);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Looks up a single live flag by id within a tenant.
     *
     * @param tenantId the owning tenant
     * @param id       the flag id
     * @return the matching flag
     * @throws FlagNotFoundException if the tenant has no live flag with that id
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public Flag getFlagById(String tenantId, String id) {
        try {
            return flagRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, tenantId)
                    .orElseThrow(FlagNotFoundException::new);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Replaces a flag's boolean value, then announces the change.
     *
     * <p>The flag is located by {@code (tenantId, id)}; the change event still
     * carries the flag's name, which is the key consumer services cache on.</p>
     *
     * @param tenantId the owning tenant
     * @param id       the flag id
     * @param value    the new boolean value
     * @return the updated flag
     * @throws FlagNotFoundException if the tenant has no live flag with that id
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public Flag updateFlag(String tenantId, String id, Boolean value) {
        Flag flag = getFlagById(tenantId, id);
        flag.setValue(value);
        flag.setUpdatedBy(currentPrincipal());

        Flag saved = saveExistingFlag(flag);

        publishSafely(saved, value, EVENT_UPDATED);
        return saved;
    }

    /**
     * Soft-deletes a flag, then announces its deletion with a {@code null} value.
     *
     * @param tenantId the owning tenant
     * @param id       the flag id
     * @throws FlagNotFoundException if the tenant has no live flag with that id
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public void deleteFlag(String tenantId, String id) {
        Flag flag = getFlagById(tenantId, id);
        flag.setDeletedAt(Instant.now());
        flag.setUpdatedBy(currentPrincipal());

        Flag saved = saveExistingFlag(flag);

        publishSafely(saved, null, EVENT_DELETED);
    }

    private boolean tenantExists(String tenantId) {
        try {
            return tenantRepository.existsById(tenantId);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Persists a brand-new flag, translating Mongo failures into domain
     * exceptions. {@link DuplicateKeyException} is a subclass of
     * {@link DataAccessException}, so it is caught first.
     */
    private Flag saveNewFlag(Flag flag) {
        try {
            return flagRepository.save(flag);
        } catch (DuplicateKeyException ex) {
            throw new FlagAlreadyExistsException(ex);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Persists a change to an existing flag. A duplicate-key violation here is
     * a storage failure, not a client error — the flag was already found.
     */
    private Flag saveExistingFlag(Flag flag) {
        try {
            return flagRepository.save(flag);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Publishes a change event without ever failing the caller's request.
     *
     * @param flag  the persisted flag
     * @param value the new value, or {@code null} for deletions
     * @param event the event type
     */
    private void publishSafely(Flag flag, Boolean value, String event) {
        try {
            flagEventPublisher.publish(flag.getTenantId(), flag.getName(), value, flag.getVersion(), event);
        } catch (Exception ex) {
            log.warn("Redis publish failed for flag {} in tenant {}: {}",
                    flag.getName(), flag.getTenantId(), ex.getMessage(), ex);
        }
    }

    /**
     * The username behind the current request, used for audit fields; falls
     * back to {@code system} for non-HTTP callers (for example migrations).
     */
    private static String currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return SYSTEM_PRINCIPAL;
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user.username();
        }
        return authentication.getName() == null ? SYSTEM_PRINCIPAL : authentication.getName();
    }
}

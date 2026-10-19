package com.ffproject.flagservice.tenant;

import com.ffproject.flagservice.auth.UserRepository;
import com.ffproject.flagservice.exception.ServiceUnavailableException;
import com.ffproject.flagservice.exception.TenantAlreadyExistsException;
import com.ffproject.flagservice.exception.TenantNotFoundException;
import com.ffproject.flagservice.flag.FlagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic for tenant lifecycle.
 *
 * <p>Deleting a tenant cascades: every flag and every user bound to it is
 * removed, so no orphaned data can be reached after the tenant is gone.</p>
 */
@Service
public class TenantService {

    private static final Logger log = LoggerFactory.getLogger(TenantService.class);

    private final TenantRepository tenantRepository;
    private final FlagRepository flagRepository;
    private final UserRepository userRepository;

    public TenantService(TenantRepository tenantRepository,
                         FlagRepository flagRepository,
                         UserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.flagRepository = flagRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a tenant.
     *
     * @param name the unique tenant name
     * @return the persisted tenant
     * @throws TenantAlreadyExistsException if the name is already taken
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public Tenant createTenant(String name) {
        if (tenantRepository.existsByName(name)) {
            throw new TenantAlreadyExistsException();
        }

        Tenant tenant = new Tenant();
        tenant.setName(name);

        try {
            return tenantRepository.save(tenant);
        } catch (DuplicateKeyException ex) {
            throw new TenantAlreadyExistsException(ex);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Lists every tenant.
     *
     * @return all tenants
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public List<Tenant> getAllTenants() {
        try {
            return tenantRepository.findAll();
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Fetches a tenant by id.
     *
     * @param id the tenant id
     * @return the tenant
     * @throws TenantNotFoundException if no tenant has that id
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public Tenant getTenant(String id) {
        try {
            return tenantRepository.findById(id).orElseThrow(TenantNotFoundException::new);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Deletes a tenant together with all of its flags and users.
     *
     * @param id the tenant id
     * @throws TenantNotFoundException if no tenant has that id
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public void deleteTenant(String id) {
        Tenant tenant = getTenant(id);

        try {
            long flagsRemoved = flagRepository.deleteByTenantId(id);
            long usersRemoved = userRepository.deleteByTenantId(id);
            tenantRepository.deleteById(id);
            log.info("Deleted tenant {} ('{}'): removed {} flags and {} users",
                    id, tenant.getName(), flagsRemoved, usersRemoved);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }
}

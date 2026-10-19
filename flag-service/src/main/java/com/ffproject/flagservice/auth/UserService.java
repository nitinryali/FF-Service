package com.ffproject.flagservice.auth;

import com.ffproject.flagservice.exception.ServiceUnavailableException;
import com.ffproject.flagservice.exception.TenantNotFoundException;
import com.ffproject.flagservice.exception.UserAlreadyExistsException;
import com.ffproject.flagservice.tenant.TenantRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Business logic for user accounts.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       TenantRepository tenantRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a tenant-bound account, hashing the password before storage.
     *
     * @param username the unique login name
     * @param password the plaintext password (never stored)
     * @param tenantId the tenant the user belongs to
     * @param role     the role to grant; defaults to {@link Role#TENANT_USER}
     * @return the persisted account
     * @throws TenantNotFoundException if the tenant does not exist
     * @throws UserAlreadyExistsException if the username is taken
     */
    public AppUser createUser(String username, String password, String tenantId, Role role) {
        if (!tenantRepository.existsById(tenantId)) {
            throw new TenantNotFoundException();
        }
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException();
        }

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setTenantId(tenantId);
        user.setRole(role == null ? Role.TENANT_USER : role);

        try {
            return userRepository.save(user);
        } catch (DuplicateKeyException ex) {
            throw new UserAlreadyExistsException(ex);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }
}

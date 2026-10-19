package com.ffproject.flagservice.flag;

import com.ffproject.flagservice.exception.FlagAlreadyExistsException;
import com.ffproject.flagservice.exception.FlagNotFoundException;
import com.ffproject.flagservice.exception.ServiceUnavailableException;
import com.ffproject.flagservice.redis.FlagEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic for flag management.
 *
 * <p>Owns the translation from persistence and messaging failures into the
 * domain exceptions the API layer understands:</p>
 * <ul>
 *   <li>duplicate name → {@link FlagAlreadyExistsException} (409)</li>
 *   <li>missing flag → {@link FlagNotFoundException} (404)</li>
 *   <li>any other Mongo outage → {@link ServiceUnavailableException} (503)</li>
 * </ul>
 *
 * <p>Redis publication only ever happens <em>after</em> a successful write, so a
 * failed write can never produce a phantom flag-change event
 * (Requirements 1.1–1.5, 1.7, 1.8, 1.10, 3.1).</p>
 */
@Service
public class FlagService {

    private static final Logger log = LoggerFactory.getLogger(FlagService.class);

    private static final String EVENT_CREATED = "created";
    private static final String EVENT_UPDATED = "updated";
    private static final String EVENT_DELETED = "deleted";

    private final FlagRepository flagRepository;
    private final FlagEventPublisher flagEventPublisher;

    public FlagService(FlagRepository flagRepository, FlagEventPublisher flagEventPublisher) {
        this.flagRepository = flagRepository;
        this.flagEventPublisher = flagEventPublisher;
    }

    /**
     * Creates and persists a new flag, then announces it.
     *
     * @param name  the unique flag name
     * @param value the initial boolean value
     * @return the persisted flag, including server-assigned id and timestamps
     * @throws FlagAlreadyExistsException if {@code name} is already in use
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public Flag createFlag(String name, Boolean value) {
        Flag flag = new Flag();
        flag.setName(name);
        flag.setValue(value);

        Flag saved = saveNewFlag(flag);

        publishSafely(name, value, EVENT_CREATED);
        return saved;
    }

    /**
     * Returns every stored flag, or an empty list when none exist.
     *
     * @return all flags
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public List<Flag> getAllFlags() {
        try {
            return flagRepository.findAll();
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Looks up a single flag by its unique name.
     *
     * @param name the flag name
     * @return the matching flag
     * @throws FlagNotFoundException if no flag has that name
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public Flag getFlagByName(String name) {
        try {
            return flagRepository.findByName(name).orElseThrow(FlagNotFoundException::new);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Looks up a single flag by its server-assigned id.
     *
     * <p>Ids are the stable identity used by the mutation endpoints
     * ({@code PATCH} and {@code DELETE}); a flag's name is only a unique
     * human-readable label and may be reused after a deletion.</p>
     *
     * @param id the flag id
     * @return the matching flag
     * @throws FlagNotFoundException if no flag has that id
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public Flag getFlagById(String id) {
        try {
            return flagRepository.findById(id).orElseThrow(FlagNotFoundException::new);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }
    }

    /**
     * Replaces a flag's boolean value, then announces the change.
     *
     * <p>The flag is located by id, but the change event is still published
     * under the flag's name, which is the key consumer services cache on.</p>
     *
     * @param id    the existing flag id
     * @param value the new boolean value
     * @return the updated flag
     * @throws FlagNotFoundException if no flag has that id
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public Flag updateFlag(String id, Boolean value) {
        Flag flag = getFlagById(id);
        flag.setValue(value);

        Flag saved = saveExistingFlag(flag);

        publishSafely(flag.getName(), value, EVENT_UPDATED);
        return saved;
    }

    /**
     * Removes a flag, then announces its deletion with a {@code null} value.
     *
     * <p>The flag is located by id so that the deletion event can still carry
     * the flag's name — the key consumer services cache on.</p>
     *
     * @param id the existing flag id
     * @throws FlagNotFoundException if no flag has that id
     * @throws ServiceUnavailableException if the store is unreachable
     */
    public void deleteFlag(String id) {
        Flag flag = getFlagById(id);
        String name = flag.getName();

        try {
            flagRepository.deleteById(id);
        } catch (DataAccessException ex) {
            throw new ServiceUnavailableException(ex);
        }

        publishSafely(name, null, EVENT_DELETED);
    }

    /**
     * Persists a brand-new flag, translating Mongo failures into domain
     * exceptions.
     *
     * <p>{@link DuplicateKeyException} is a subclass of {@link DataAccessException},
     * so it must be caught first.</p>
     *
     * @param flag the document to insert
     * @return the persisted document
     * @throws FlagAlreadyExistsException if the name is already taken
     * @throws ServiceUnavailableException if the store is unreachable
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
     * Persists a change to an existing flag.
     *
     * <p>A duplicate-key violation on an update is not a client error — the flag
     * was already found — so it is treated as a storage failure.</p>
     *
     * @param flag the document to save
     * @return the persisted document
     * @throws ServiceUnavailableException if the store is unreachable
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
     * @param name  the flag name
     * @param value the new value, or {@code null} for deletions
     * @param event the event type
     */
    private void publishSafely(String name, Boolean value, String event) {
        try {
            flagEventPublisher.publish(name, value, event);
        } catch (Exception ex) {
            log.warn("Redis publish failed for flag {}: {}", name, ex.getMessage(), ex);
        }
    }
}

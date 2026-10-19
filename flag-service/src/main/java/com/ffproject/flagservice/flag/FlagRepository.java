package com.ffproject.flagservice.flag;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for the Flag document collection.
 *
 * <p>Provides CRUD operations and custom query methods for managing
 * feature flags in the MongoDB database.</p>
 *
 * @see Flag
 */
@Repository
public interface FlagRepository extends MongoRepository<Flag, String> {

    /**
     * Finds a Flag by its unique name.
     *
     * @param name the unique name of the flag to find
     * @return an Optional containing the Flag if found, or empty if not found
     */
    Optional<Flag> findByName(String name);

    /**
     * Checks if a Flag with the given name exists.
     *
     * @param name the unique name to check for existence
     * @return true if a flag with the name exists, false otherwise
     */
    boolean existsByName(String name);
}
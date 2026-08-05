package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.ApiKey;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for API key persistence operations.
 *
 * <p>Adapters implementing this port handle storage and retrieval of API keys.
 * Key lookup by hash is the primary authentication path — the raw key is
 * never stored.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-08-04
 */
public interface ApiKeyPort {

    /**
     * Persists a new API key record.
     */
    void save(ApiKey apiKey);

    /**
     * Finds an API key by its SHA-256 hash.
     */
    Optional<ApiKey> findByKeyHash(String keyHash);

    /**
     * Returns all API keys belonging to the given owner email.
     */
    List<ApiKey> findAllByOwnerEmail(String ownerEmail);

    /**
     * Deletes an API key by its ID.
     */
    void deleteById(String id);

    /**
     * Checks whether an API key with the given ID exists.
     */
    boolean existsById(String id);

    /**
     * Finds an API key by its ID.
     */
    Optional<ApiKey> findById(String id);

    /**
     * Returns the number of API keys owned by the given email.
     */
    int countByOwnerEmail(String ownerEmail);
}

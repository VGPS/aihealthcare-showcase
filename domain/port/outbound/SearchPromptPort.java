package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and retrieving search engine prompt configurations.
 *
 * <p>Adapters implementing this port store {@link SearchPromptConfig} records in a
 * backing store (e.g. a relational database). The port is used by harvesters such
 * as {@code PerplexityHarvester} to look up the active query template for a given
 * search engine at harvest time.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
public interface SearchPromptPort {

    /**
     * Returns the search prompt config for the given engine identifier, if one exists.
     *
     * @param engine Engine identifier (e.g. {@code "GOOGLE"}, {@code "PERPLEXITY"}).
     *               Case-sensitive.
     * @return An {@link Optional} containing the config, or empty if none is stored.
     */
    Optional<SearchPromptConfig> findByEngine(String engine);

    /**
     * Returns all stored search prompt configs, active and inactive.
     *
     * @return List of all configs; empty list if none exist.
     */
    List<SearchPromptConfig> findAll();

    /**
     * Persists a search prompt config, inserting or updating as appropriate.
     *
     * @param config The config to save. Must not be null.
     */
    void save(SearchPromptConfig config);
}

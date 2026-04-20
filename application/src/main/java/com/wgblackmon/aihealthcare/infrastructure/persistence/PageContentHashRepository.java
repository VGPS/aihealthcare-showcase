package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PageContentHashEntity}.
 *
 * <p>Provides lookup of the stored SHA-256 content hash for a monitored page
 * URL, enabling the change-detection pipeline in
 * {@link com.wgblackmon.aihealthcare.infrastructure.ingestion.web.WebPageHarvester}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
public interface PageContentHashRepository extends JpaRepository<PageContentHashEntity, String> {

    /**
     * Finds the stored content hash for the given page URL.
     *
     * @param pageUrl the monitored page URL
     * @return the entity if found, empty otherwise
     */
    Optional<PageContentHashEntity> findByPageUrl(String pageUrl);
}

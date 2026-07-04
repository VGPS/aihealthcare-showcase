package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link WikiSourceRefEntity}.
 *
 * <p>Supports retrieval of provenance records by page slug, enabling
 * the wiki query adapter to populate {@code WikiPage.sources()} when
 * mapping entities to domain records.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public interface WikiSourceRefRepository extends JpaRepository<WikiSourceRefEntity, Long> {

    /**
     * Finds all source references for the given wiki page.
     *
     * @param pageSlug the slug of the wiki page
     * @return source references for that page; empty if none
     */
    List<WikiSourceRefEntity> findByPageSlug(String pageSlug);

    /**
     * Deletes all source references for the given wiki page.
     * Used when replacing sources during a page update.
     *
     * @param pageSlug the slug of the wiki page
     */
    void deleteByPageSlug(String pageSlug);
}

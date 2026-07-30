package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link LegalTrendSnapshotEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
public interface LegalTrendSnapshotRepository extends JpaRepository<LegalTrendSnapshotEntity, Long> {

    /**
     * Returns the most recently generated legal trend snapshot.
     *
     * @return the latest snapshot entity, or empty
     */
    Optional<LegalTrendSnapshotEntity> findTopByOrderByGeneratedAtDesc();
}

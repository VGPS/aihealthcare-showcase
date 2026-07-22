package com.wgblackmon.aihealthcare.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link TrendSnapshotEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
public interface TrendSnapshotRepository extends JpaRepository<TrendSnapshotEntity, Long> {

    /**
     * Returns the most recently generated snapshot.
     *
     * @return the latest snapshot entity, or empty
     */
    Optional<TrendSnapshotEntity> findTopByOrderByGeneratedAtDesc();

    /**
     * Returns all snapshots ordered by generation time descending.
     *
     * @return all snapshot entities, newest first
     */
    List<TrendSnapshotEntity> findAllByOrderByGeneratedAtDesc();
}

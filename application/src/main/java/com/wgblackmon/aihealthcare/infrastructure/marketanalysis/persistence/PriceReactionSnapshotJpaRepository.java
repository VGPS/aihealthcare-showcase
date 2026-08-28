package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link PriceReactionSnapshotEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
public interface PriceReactionSnapshotJpaRepository extends JpaRepository<PriceReactionSnapshotEntity, Long> {

    List<PriceReactionSnapshotEntity> findByEntryId(String entryId);

    boolean existsByEntryIdAndHorizon(String entryId, String horizon);
}

package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link MarketDigestEntryEntity}.
 *
 * <p>Entries are always loaded or deleted by their parent {@code digest_id} —
 * no JPA cascade is used. The adapter manages child lifecycle explicitly.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface MarketDigestEntryJpaRepository extends JpaRepository<MarketDigestEntryEntity, String> {

    List<MarketDigestEntryEntity> findByDigestId(String digestId);

    void deleteByDigestId(String digestId);
}

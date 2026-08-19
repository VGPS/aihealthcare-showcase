package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link MarketDigestEntity}.
 *
 * <p>Finder and delete methods are used by {@link MarketDigestRepositoryAdapter}
 * to implement the upsert pattern: locate an existing digest by date, delete it
 * (and its children) explicitly, then insert the new version.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface MarketDigestJpaRepository extends JpaRepository<MarketDigestEntity, String> {

    Optional<MarketDigestEntity> findByDigestDate(LocalDate digestDate);

    void deleteByDigestDate(LocalDate digestDate);

    Optional<MarketDigestEntity> findTopByOrderByDigestDateDesc();

    List<MarketDigestEntity> findAllByOrderByDigestDateDesc();
}

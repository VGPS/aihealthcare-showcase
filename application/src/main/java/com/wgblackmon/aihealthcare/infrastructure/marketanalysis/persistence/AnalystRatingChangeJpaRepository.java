package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

/**
 * Spring Data JPA repository for {@link AnalystRatingChangeEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface AnalystRatingChangeJpaRepository extends JpaRepository<AnalystRatingChangeEntity, Long> {

    List<AnalystRatingChangeEntity> findByTickerSymbolAndChangedAtGreaterThanEqualOrderByChangedAtDesc(
            String tickerSymbol, Instant since);
}

package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link TickerWatchlistEntity}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface TickerWatchlistJpaRepository extends JpaRepository<TickerWatchlistEntity, Long> {

    List<TickerWatchlistEntity> findBySubscriberIdOrderByAddedAtAsc(String subscriberId);

    Optional<TickerWatchlistEntity> findBySubscriberIdAndTickerSymbol(String subscriberId, String tickerSymbol);

    void deleteBySubscriberId(String subscriberId);

    boolean existsBySubscriberIdAndTickerSymbol(String subscriberId, String tickerSymbol);
}

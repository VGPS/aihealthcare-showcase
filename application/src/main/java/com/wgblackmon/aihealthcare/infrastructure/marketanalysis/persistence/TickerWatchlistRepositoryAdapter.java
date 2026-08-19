package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.TickerWatchlistRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA-backed adapter implementing {@link TickerWatchlistRepository}.
 *
 * <p>Each ticker is stored as a separate row keyed by (subscriberId, tickerSymbol).
 * {@link #addTicker} is idempotent — it checks for the existing row before inserting.
 * {@link #replaceWatchlist} uses a bulk delete + flush + insert to avoid constraint
 * violations under Hibernate batching.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class TickerWatchlistRepositoryAdapter implements TickerWatchlistRepository {

    private final TickerWatchlistJpaRepository repo;

    public TickerWatchlistRepositoryAdapter(TickerWatchlistJpaRepository repo) {
        this.repo = repo;
    }

    @Override
    public List<String> findWatchedTickers(String subscriberId) {
        log.debug("findWatchedTickers() | subscriberId={}", subscriberId);

        List<TickerWatchlistEntity> entities =
                repo.findBySubscriberIdOrderByAddedAtAsc(subscriberId);

        List<String> result = new ArrayList<>();
        for (TickerWatchlistEntity entity : entities) {
            result.add(entity.getTickerSymbol());
        }

        log.debug("findWatchedTickers() | return.size={}", result.size());
        return result;
    }

    @Override
    @Transactional
    public void addTicker(String subscriberId, String ticker) {
        log.debug("addTicker() | subscriberId={}, ticker={}", subscriberId, ticker);

        if (!repo.existsBySubscriberIdAndTickerSymbol(subscriberId, ticker)) {
            repo.save(new TickerWatchlistEntity(subscriberId, ticker, Instant.now()));
        }

        log.debug("addTicker() | return=void");
    }

    @Override
    @Transactional
    public void removeTicker(String subscriberId, String ticker) {
        log.debug("removeTicker() | subscriberId={}, ticker={}", subscriberId, ticker);

        repo.findBySubscriberIdAndTickerSymbol(subscriberId, ticker)
                .ifPresent(repo::delete);

        log.debug("removeTicker() | return=void");
    }

    @Override
    @Transactional
    public void replaceWatchlist(String subscriberId, List<String> tickers) {
        log.debug("replaceWatchlist() | subscriberId={}, tickers={}", subscriberId, tickers);

        repo.deleteBySubscriberId(subscriberId);
        repo.flush();

        Instant now = Instant.now();
        for (String ticker : tickers) {
            repo.save(new TickerWatchlistEntity(subscriberId, ticker, now));
        }

        log.debug("replaceWatchlist() | return=void");
    }
}

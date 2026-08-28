package com.wgblackmon.aihealthcare.domain.marketanalysis;

import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketDataPort;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.PriceReactionPort;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Measures how the market actually reacted to each qualifying {@link MarketDigestEntry}
 * by comparing a pre-news baseline price against the price observed at fixed
 * {@link ReactionHorizon} offsets afterward.
 *
 * <p>Intended to be polled on a short interval (see {@code MarketAnalysisScheduler}) —
 * each call to {@link #capturePendingReactions} only measures horizons that are
 * both due and not yet captured, so re-running frequently is safe and cheap.
 *
 * <p>A missing baseline, missing observed price, or zero baseline (division-by-zero
 * guard) silently skips that horizon rather than failing the whole poll — consistent
 * with {@link MarketDataPort}'s fail-open contract.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
@Slf4j
public class PriceReactionService {

    /** Covers the longest horizon (THREE_DAY) plus a buffer for late-running polls. */
    private static final Duration LOOKBACK = Duration.ofDays(5);

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final PriceReactionPort reactionPort;
    private final MarketDataPort marketDataPort;

    public PriceReactionService(PriceReactionPort reactionPort, MarketDataPort marketDataPort) {
        this.reactionPort = reactionPort;
        this.marketDataPort = marketDataPort;
    }

    /**
     * Finds every ticker-bearing digest entry published within the lookback window,
     * measures any horizon that is due and not yet captured, and persists the results.
     *
     * @param now the current instant (injected so tests can control timing)
     * @return the snapshots newly captured by this call; empty list if none were due
     */
    public List<PriceReactionSnapshot> capturePendingReactions(Instant now) {
        log.debug("capturePendingReactions() | now={}", now);

        Instant since = now.minus(LOOKBACK);
        List<TrackedCompanyEntry> candidates = reactionPort.findTickerEntriesPublishedAfter(since);

        List<PriceReactionSnapshot> captured = new ArrayList<>();
        for (TrackedCompanyEntry candidate : candidates) {
            for (ReactionHorizon horizon : ReactionHorizon.values()) {
                Instant dueAt = candidate.publishedAt().plus(horizon.offset());
                if (now.isBefore(dueAt)) {
                    continue;
                }
                if (reactionPort.existsByEntryIdAndHorizon(candidate.entryId(), horizon)) {
                    continue;
                }

                Optional<PriceReactionSnapshot> snapshot = buildSnapshot(candidate, horizon, dueAt, now);
                if (snapshot.isPresent()) {
                    reactionPort.save(snapshot.get());
                    captured.add(snapshot.get());
                }
            }
        }

        log.debug("capturePendingReactions() | return.size={}", captured.size());
        return captured;
    }

    // ─── private helpers ────────────────────────────────────────────────────

    private Optional<PriceReactionSnapshot> buildSnapshot(TrackedCompanyEntry candidate,
                                                            ReactionHorizon horizon,
                                                            Instant dueAt,
                                                            Instant now) {
        Optional<BigDecimal> baseline = resolveBaseline(candidate.tickerSymbol(), candidate.publishedAt());
        if (baseline.isEmpty() || baseline.get().compareTo(BigDecimal.ZERO) == 0) {
            log.debug("buildSnapshot() | no usable baseline for ticker={}, entryId={}",
                    candidate.tickerSymbol(), candidate.entryId());
            return Optional.empty();
        }

        Optional<BigDecimal> observed = resolveObserved(candidate.tickerSymbol(), horizon, dueAt);
        if (observed.isEmpty()) {
            log.debug("buildSnapshot() | no observed price for ticker={}, entryId={}, horizon={}",
                    candidate.tickerSymbol(), candidate.entryId(), horizon);
            return Optional.empty();
        }

        BigDecimal pctChange = observed.get().subtract(baseline.get())
                .divide(baseline.get(), 6, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED)
                .setScale(2, RoundingMode.HALF_UP);

        return Optional.of(new PriceReactionSnapshot(
                candidate.entryId(),
                candidate.tickerSymbol(),
                horizon,
                baseline.get(),
                observed.get(),
                pctChange,
                now
        ));
    }

    private Optional<BigDecimal> resolveBaseline(String tickerSymbol, Instant publishedAt) {
        LocalDate publishedDate = publishedAt.atZone(ZoneOffset.UTC).toLocalDate();
        Optional<PriceHistory> history = marketDataPort.getPriceHistory(
                tickerSymbol, publishedDate.minusDays(10), publishedDate);
        if (history.isEmpty()) {
            return Optional.empty();
        }

        PriceBar latestBefore = null;
        for (PriceBar bar : history.get().bars()) {
            if (bar.date().isBefore(publishedDate)
                    && (latestBefore == null || bar.date().isAfter(latestBefore.date()))) {
                latestBefore = bar;
            }
        }
        return latestBefore == null ? Optional.empty() : Optional.of(latestBefore.close());
    }

    private Optional<BigDecimal> resolveObserved(String tickerSymbol, ReactionHorizon horizon, Instant dueAt) {
        if (horizon.isIntraday()) {
            return marketDataPort.getQuote(tickerSymbol).map(Quote::price);
        }

        LocalDate dueDate = dueAt.atZone(ZoneOffset.UTC).toLocalDate();
        Optional<PriceHistory> history = marketDataPort.getPriceHistory(
                tickerSymbol, dueDate.minusDays(5), dueDate);
        if (history.isEmpty()) {
            return Optional.empty();
        }

        PriceBar latestOnOrBefore = null;
        for (PriceBar bar : history.get().bars()) {
            if (!bar.date().isAfter(dueDate)
                    && (latestOnOrBefore == null || bar.date().isAfter(latestOnOrBefore.date()))) {
                latestOnOrBefore = bar;
            }
        }
        return latestOnOrBefore == null ? Optional.empty() : Optional.of(latestOnOrBefore.close());
    }
}

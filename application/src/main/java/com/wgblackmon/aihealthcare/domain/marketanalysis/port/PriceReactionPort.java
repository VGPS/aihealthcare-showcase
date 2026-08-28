package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionSnapshot;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ReactionHorizon;
import com.wgblackmon.aihealthcare.domain.marketanalysis.TrackedCompanyEntry;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for persisting {@link PriceReactionSnapshot} rows and for discovering
 * which persisted, ticker-bearing {@link com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry}
 * records still need a reaction check.
 *
 * <p>{@link #findTickerEntriesPublishedAfter} sources {@code entryId} from the persistence
 * layer (joining {@code market_digest_entry} and {@code market_digest_affected_company})
 * rather than from a domain {@code MarketDigest}, since the domain aggregate itself carries
 * no identifier until it has been saved.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-28
 * @updated 2026-08-28
 */
public interface PriceReactionPort {

    /**
     * Persists a new reaction snapshot. Callers are responsible for checking
     * {@link #existsByEntryIdAndHorizon} first — duplicate rows are allowed here.
     *
     * @param snapshot the snapshot to save (non-null)
     */
    void save(PriceReactionSnapshot snapshot);

    /**
     * Returns all reaction snapshots captured so far for the given entry, in no
     * particular order.
     *
     * @param entryId the {@code MarketDigestEntry} id (non-blank)
     * @return matching snapshots; empty list if none exist
     */
    List<PriceReactionSnapshot> findByEntryId(String entryId);

    /**
     * Returns {@code true} if a snapshot already exists for the given entry and horizon,
     * so the poller can skip re-measuring it.
     *
     * @param entryId the {@code MarketDigestEntry} id (non-blank)
     * @param horizon the horizon to check (non-null)
     */
    boolean existsByEntryIdAndHorizon(String entryId, ReactionHorizon horizon);

    /**
     * Returns one {@link TrackedCompanyEntry} for every ticker-bearing affected company
     * on a {@code MarketDigestEntry} published at or after {@code since}.
     *
     * @param since lower bound (inclusive) on {@code publishedAt} (non-null)
     * @return matching tracked companies; empty list if none exist
     */
    List<TrackedCompanyEntry> findTickerEntriesPublishedAfter(Instant since);

    /**
     * Returns all reaction snapshots captured for the {@code MarketDigestEntry} whose
     * news item was published at exactly {@code publishedAt} and which has an affected
     * company matching {@code tickerSymbol}.
     *
     * <p>Used by the read side ({@code MarketDashboardController}, {@code MarketDigestController})
     * to join a domain {@link com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry}
     * — which carries no id of its own — back to its snapshots without needing one. The
     * (ticker, publishedAt) pair is effectively unique in practice: each entry's publishedAt
     * comes from a distinct research item, and near-duplicate stories are already suppressed
     * upstream by embedding dedup.
     *
     * @param tickerSymbol exchange ticker symbol (non-blank)
     * @param publishedAt  the entry's news item publish time (non-null)
     * @return matching snapshots, in no particular order; empty list if none exist
     */
    List<PriceReactionSnapshot> findByTickerAndPublishedAt(String tickerSymbol, Instant publishedAt);
}

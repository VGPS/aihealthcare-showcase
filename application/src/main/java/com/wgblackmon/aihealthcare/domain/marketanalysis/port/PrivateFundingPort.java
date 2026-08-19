package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.PeerGroup;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PrivateFundingRound;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for persisting and retrieving private funding rounds.
 *
 * <p>Rounds are saved with a resolved {@link PeerGroup} (assigned by
 * {@code PeerGroupTagger} at save time) so the {@link #findRecentRounds}
 * query can filter by peer group without re-running keyword matching.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface PrivateFundingPort {

    /**
     * Persists a new funding round with its resolved peer group.
     *
     * @param round     the round to save (non-null)
     * @param peerGroup peer group resolved from the company name/ticker (non-null)
     */
    void save(PrivateFundingRound round, PeerGroup peerGroup);

    /**
     * Returns all funding rounds announced on or after {@code since}, optionally
     * filtered by peer group.
     *
     * @param since           lower bound for {@code announcedAt} (inclusive, non-null)
     * @param peerGroupFilter peer group to filter by, or {@code null} to return all groups
     */
    List<PrivateFundingRound> findRecentRounds(Instant since, PeerGroup peerGroupFilter);
}

package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Outbound port for persisting and querying {@link MarketDigest} aggregates.
 *
 * <p>The JPA adapter ({@code MarketDigestRepositoryAdapter}) implements this port
 * against the {@code market_digest} / {@code market_digest_entry} tables introduced
 * in Slice 1.3. Saving twice for the same date upserts rather than throwing, since
 * daily jobs may be re-run manually.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface MarketDigestRepository {

    /**
     * Persists the given digest, including all entries, impact assessments, and
     * affected companies, in a single transaction. Upserts on {@code digest.date()}.
     *
     * @param digest the digest to save (non-null)
     */
    void save(MarketDigest digest);

    /**
     * Returns the digest for the given date, or empty if none has been persisted yet.
     *
     * @param date the digest date to look up (non-null)
     * @return the digest wrapped in Optional, or empty
     */
    Optional<MarketDigest> findByDate(LocalDate date);

    /**
     * Returns the most recently generated digest, or empty if none exists yet.
     *
     * @return the latest digest wrapped in Optional, or empty
     */
    Optional<MarketDigest> findLatest();

    /**
     * Returns all persisted digests ordered by date descending (newest first).
     *
     * @return list of all digests; empty list if none exist
     */
    List<MarketDigest> findAll();
}

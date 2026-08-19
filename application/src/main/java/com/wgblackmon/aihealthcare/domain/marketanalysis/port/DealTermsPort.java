package com.wgblackmon.aihealthcare.domain.marketanalysis.port;

import com.wgblackmon.aihealthcare.domain.marketanalysis.DealTerms;

import java.util.Optional;

/**
 * Outbound port for persisting and retrieving deal term details.
 *
 * <p>Each {@link DealTerms} is keyed by the digest entry's headline, which
 * serves as a natural surrogate key for M&amp;A entries (headlines are
 * sourced from the LLM classifier and are treated as stable within a digest).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
public interface DealTermsPort {

    /**
     * Persists deal terms keyed by the entry headline.
     * Replaces any existing terms for the same headline.
     *
     * @param entryHeadline the M&amp;A entry headline (non-null, non-blank)
     * @param terms         the deal terms to persist (non-null)
     */
    void save(String entryHeadline, DealTerms terms);

    /**
     * Returns deal terms for the given entry headline, or empty if none stored.
     *
     * @param entryHeadline the entry headline to look up (non-null, non-blank)
     */
    Optional<DealTerms> findByEntryHeadline(String entryHeadline);
}

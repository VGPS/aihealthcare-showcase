package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewBillCandidate;
import com.wgblackmon.aihealthcare.domain.model.StateLaw;

import java.util.List;

/**
 * Outbound port for persisting and managing {@link NewBillCandidate} records
 * discovered by the Perplexity legislation discovery pipeline.
 *
 * <p>Candidates require human review. An admin may promote a candidate
 * (creating a {@link StateLaw} in the registry) or dismiss it. Nothing
 * is auto-written to the registry.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-06
 * @updated 2026-09-06
 */
public interface NewBillCandidatePort {

    /**
     * Persists a new bill candidate.
     */
    void saveCandidate(NewBillCandidate candidate);

    /**
     * Returns all candidates that have not yet been reviewed.
     */
    List<NewBillCandidate> findUnreviewed();

    /**
     * Marks a candidate as reviewed without promoting or dismissing.
     */
    void markReviewed(Long candidateId);

    /**
     * Promotes a candidate to the legislation registry by creating the
     * given {@link StateLaw} and linking the candidate to it.
     */
    void promote(Long candidateId, StateLaw law);

    /**
     * Dismisses a candidate, marking it as reviewed and not promoted.
     */
    void dismiss(Long candidateId);
}

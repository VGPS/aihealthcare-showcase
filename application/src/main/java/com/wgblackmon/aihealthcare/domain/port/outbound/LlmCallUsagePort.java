package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.LlmCallUsage;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for persisting and querying LLM call token usage.
 *
 * <p>Implementations store {@link LlmCallUsage} rows — one per completed LLM
 * call — enabling a cost breakdown by model and by pipeline that survives
 * application restarts, independent of the Anthropic Console's own usage page.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public interface LlmCallUsagePort {

    /**
     * Persists a single LLM call's token usage.
     *
     * @param usage the usage record to save
     */
    void save(LlmCallUsage usage);

    /**
     * Returns all usage rows recorded since the given instant, ordered by
     * occurred-at descending.
     *
     * @param since the earliest instant (inclusive) to include
     * @return list of usage rows; empty if none exist
     */
    List<LlmCallUsage> findSince(Instant since);
}

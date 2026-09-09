package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DataFeed;
import com.wgblackmon.aihealthcare.domain.model.DataQueryPlan;

/**
 * Resolves free-text customer input into a constrained {@link DataQueryPlan}.
 *
 * <p>Implemented by an LLM adapter using {@code BeanOutputConverter}. The
 * returned plan is <em>always</em> validated by the caller before execution.
 * The implementation must never emit SQL, a table name, or a field outside
 * {@code DataQueryPlan}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface PromptToQueryPort {

    /**
     * Resolves free text into a query plan.
     *
     * @param promptText the customer's natural-language question
     * @param feed       the target feed (provides parameter schema for the LLM)
     * @return a validated plan, or {@code null} if the input is unresolvable
     */
    DataQueryPlan resolve(String promptText, DataFeed feed);
}

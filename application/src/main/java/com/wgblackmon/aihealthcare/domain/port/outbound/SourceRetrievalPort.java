package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.RetrievalQuery;
import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;

import java.util.List;

/**
 * Outbound port — retrieve source documents from an external search backend.
 *
 * <p>Implementations live in {@code infrastructure/research} and are swapped at
 * configuration time via the {@code aihealthcare.research.mode} property:
 * <ul>
 *   <li>{@code LegacyGoogleResearchAdapter} — wraps the existing article-ingestion
 *       store; always available, requires no API keys.</li>
 *   <li>{@code PerplexityResearchAdapter} — calls the Perplexity Sonar API; falls back
 *       to an empty result set when {@code PERPLEXITY_API_KEY} is not configured.</li>
 * </ul>
 *
 * <p>Both implementations return a {@code List<RetrievedSource>} using the same uniform
 * record, so the orchestrator and synthesis service are fully decoupled from the backend.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
public interface SourceRetrievalPort {

    /**
     * Retrieve source documents matching the given query.
     *
     * @param query The query descriptor including search text, engine hint, and result limit.
     * @return Matching sources; never {@code null}, may be empty.
     */
    List<RetrievedSource> retrieve(RetrievalQuery query);
}

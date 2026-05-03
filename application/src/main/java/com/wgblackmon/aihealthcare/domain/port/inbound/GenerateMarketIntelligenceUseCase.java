package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.MarketIntelligenceReport;

/**
 * Inbound port — drive generation of a Healthcare AI market intelligence report.
 *
 * <p>The implementation lives in {@code domain.service} and orchestrates calls to
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.SearchPromptPort} (to load the
 * configured prompt) and {@link com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort}
 * (to call the AI model and receive the HTML report back).
 *
 * <p>This port is intentionally narrow — it takes no parameters because the prompt
 * and AI configuration are fully managed through {@code search_prompts} (engine key
 * {@code MARKET_INTELLIGENCE}) and {@code application.yml}.  Callers simply invoke
 * {@link #generate()} and receive a complete, dated report.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
public interface GenerateMarketIntelligenceUseCase {

    /**
     * Generate a Healthcare AI market intelligence report using the prompt stored
     * under the {@code MARKET_INTELLIGENCE} engine key.
     *
     * @return A {@link MarketIntelligenceReport} containing the generated HTML and metadata.
     * @throws IllegalStateException if no active {@code MARKET_INTELLIGENCE} prompt is configured.
     */
    MarketIntelligenceReport generate();
}

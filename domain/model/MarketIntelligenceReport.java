package com.wgblackmon.aihealthcare.domain.model;

import java.time.LocalDate;

/**
 * Immutable domain record representing a single generated market intelligence report.
 *
 * <p>A report is produced by {@link com.wgblackmon.aihealthcare.domain.port.inbound.GenerateMarketIntelligenceUseCase}
 * by sending a configured prompt to the AI and capturing the HTML response.
 * Reports are dated so that successive runs produce independently identifiable files.
 *
 * <p>This record carries no Spring or infrastructure dependencies — it is a pure
 * value object suitable for use in domain services and application layer tests.
 *
 * @param reportDate   The calendar date on which this report was generated.
 * @param htmlContent  The full HTML document returned by the AI model.
 * @param promptEngine The engine key (e.g. {@code "MARKET_INTELLIGENCE"}) identifying
 *                     which {@link com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig}
 *                     prompt was used to generate this report.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
public record MarketIntelligenceReport(
        LocalDate reportDate,
        String htmlContent,
        String promptEngine
) {
    /**
     * Compact constructor — validates that all required fields are non-null and non-blank.
     */
    public MarketIntelligenceReport {
        if (reportDate == null) {
            throw new IllegalArgumentException("reportDate must not be null");
        }
        if (htmlContent == null || htmlContent.isBlank()) {
            throw new IllegalArgumentException("htmlContent must not be blank");
        }
        if (promptEngine == null || promptEngine.isBlank()) {
            throw new IllegalArgumentException("promptEngine must not be blank");
        }
    }
}

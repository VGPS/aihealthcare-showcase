package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.MarketIntelligenceReport;
import com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateMarketIntelligenceUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SearchPromptPort;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Application-layer service that implements {@link GenerateMarketIntelligenceUseCase}.
 *
 * <p>This service orchestrates the two-step market intelligence generation pipeline:
 * <ol>
 *   <li>Load the prompt template stored under engine key {@code MARKET_INTELLIGENCE}
 *       from the {@link SearchPromptPort} (database-backed, editable via REST API).</li>
 *   <li>Send the prompt to the AI model via {@link AiReportPort} and wrap the response
 *       in a dated {@link MarketIntelligenceReport} record.</li>
 * </ol>
 *
 * <p>This service intentionally performs no file I/O — writing the report to disk is an
 * infrastructure concern handled by callers in {@code infrastructure.config}
 * ({@link com.wgblackmon.aihealthcare.infrastructure.scheduler.MarketIntelligenceScheduler}).
 * This separation keeps this service testable without a filesystem or Spring context.
 *
 * <p>This class is not annotated with {@code @Service} — it is wired as a bean
 * in {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig} so that
 * the domain layer remains free of Spring imports.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-02
 * @updated 2026-05-02
 */
@Slf4j
public class MarketIntelligenceService implements GenerateMarketIntelligenceUseCase {

    static final String PROMPT_ENGINE = "MARKET_INTELLIGENCE";

    private final SearchPromptPort searchPromptPort;
    private final AiReportPort     aiReportPort;

    /**
     * Constructs the service with its required outbound port dependencies.
     *
     * @param searchPromptPort Port for loading the configured prompt template by engine key.
     * @param aiReportPort     Port for sending the prompt to the AI model.
     */
    public MarketIntelligenceService(SearchPromptPort searchPromptPort,
                                     AiReportPort aiReportPort) {
        log.debug("MarketIntelligenceService() | searchPromptPort={}, aiReportPort={}",
                  searchPromptPort.getClass().getSimpleName(),
                  aiReportPort.getClass().getSimpleName());
        this.searchPromptPort = searchPromptPort;
        this.aiReportPort     = aiReportPort;
        log.debug("MarketIntelligenceService() | return=void");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Loads the {@code MARKET_INTELLIGENCE} prompt from the database, sends it to
     * the AI, and wraps the response in a {@link MarketIntelligenceReport} dated today.
     *
     * @throws IllegalStateException if no active {@code MARKET_INTELLIGENCE} prompt exists.
     */
    @Override
    public MarketIntelligenceReport generate() {
        log.debug("generate() | starting market intelligence generation");
        log.info("generate() | loading prompt for engine={}", PROMPT_ENGINE);

        Optional<SearchPromptConfig> promptOpt = searchPromptPort.findByEngine(PROMPT_ENGINE);
        if (promptOpt.isEmpty()) {
            log.error("generate() | No prompt configured for engine={}", PROMPT_ENGINE);
            throw new IllegalStateException(
                    "No active MARKET_INTELLIGENCE prompt configured. "
                    + "Add a search_prompts entry with engine='MARKET_INTELLIGENCE' via "
                    + "PUT /api/v1/search-prompts/MARKET_INTELLIGENCE");
        }

        SearchPromptConfig config = promptOpt.get();
        log.info("generate() | sending prompt to AI: name='{}', promptLength={} chars",
                 config.name(), config.templateText().length());

        String htmlContent = aiReportPort.generate(config.templateText());
        log.info("generate() | AI response received: {} chars", htmlContent.length());

        MarketIntelligenceReport result = new MarketIntelligenceReport(
                LocalDate.now(),
                htmlContent,
                PROMPT_ENGINE
        );

        log.info("generate() | report generated for date={}", result.reportDate());
        log.debug("generate() | return={}", result.reportDate());
        return result;
    }
}

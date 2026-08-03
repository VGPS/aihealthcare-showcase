package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.IntelReport;
import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateIntelReportUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.IntelReportPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service that orchestrates competitive intelligence report generation.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Run COMBINED research via {@link ConductResearchUseCase} to gather sources.</li>
 *   <li>Build an intel-report prompt from the gathered citations.</li>
 *   <li>Call {@link AiReportPort} for a deep-dive AI synthesis.</li>
 *   <li>Persist the resulting {@link IntelReport} via {@link IntelReportPort}.</li>
 * </ol>
 *
 * <p>This class has no Spring annotations — it is wired as a bean in
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
public class IntelReportService implements GenerateIntelReportUseCase {

    private static final int MAX_SOURCES = 30;

    private final ConductResearchUseCase researchUseCase;
    private final AiReportPort aiReportPort;
    private final IntelReportPort intelReportPort;
    private final String intelReportTemplate;

    /**
     * Constructs the service with all required dependencies.
     *
     * @param researchUseCase     existing research pipeline for source gathering
     * @param aiReportPort        generic AI prompt-to-text port
     * @param intelReportPort     persistence port for intel reports
     * @param intelReportTemplate prompt template with {query} and {sources} placeholders
     */
    public IntelReportService(ConductResearchUseCase researchUseCase,
                              AiReportPort aiReportPort,
                              IntelReportPort intelReportPort,
                              String intelReportTemplate) {
        log.debug("IntelReportService() | researchUseCase={}, aiReportPort={}, intelReportPort={}",
                  researchUseCase.getClass().getSimpleName(),
                  aiReportPort.getClass().getSimpleName(),
                  intelReportPort.getClass().getSimpleName());
        this.researchUseCase = researchUseCase;
        this.aiReportPort = aiReportPort;
        this.intelReportPort = intelReportPort;
        this.intelReportTemplate = intelReportTemplate;
        log.debug("IntelReportService() | return=void");
    }

    @Override
    public IntelReport generate(String query, String userEmail) {
        log.debug("generate() | query={}, userEmail={}", query, userEmail);

        // 1. Gather sources via COMBINED research pipeline
        ResearchRequest request = new ResearchRequest(query, ResearchMode.COMBINED, null, MAX_SOURCES);
        ResearchAnswer answer = researchUseCase.conduct(request);
        log.info("generate() | research pipeline returned {} citations for query='{}'",
                 answer.allCitations().size(), query);

        // 2. Build the intel report prompt
        String sourceListing = buildSourceListing(answer.allCitations());
        String prompt = intelReportTemplate
                .replace("{query}", query)
                .replace("{sources}", sourceListing)
                .replace("{sourceCount}", String.valueOf(answer.allCitations().size()));

        log.info("generate() | sending intel report prompt to AI ({} chars)", prompt.length());

        // 3. Generate the deep-dive report via AI
        String htmlContent = stripMarkdownFences(aiReportPort.generate(prompt));
        log.info("generate() | AI response received ({} chars)", htmlContent.length());

        // 4. Persist and return
        IntelReport report = new IntelReport(
                UUID.randomUUID().toString(),
                query,
                htmlContent,
                answer.allCitations().size(),
                userEmail,
                Instant.now(),
                answer.allCitations());

        intelReportPort.save(report);
        log.info("generate() | report persisted: reportId={}", report.reportId());

        log.debug("generate() | return={}", report.reportId());
        return report;
    }

    @Override
    public Optional<IntelReport> findById(String reportId) {
        log.debug("findById() | reportId={}", reportId);
        Optional<IntelReport> result = intelReportPort.findById(reportId);
        log.debug("findById() | return={}", result.isPresent() ? result.get().reportId() : "empty");
        return result;
    }

    @Override
    public List<IntelReport> findAll() {
        log.debug("findAll() | (no args)");
        List<IntelReport> result = intelReportPort.findAll();
        log.debug("findAll() | return={} reports", result.size());
        return result;
    }

    /**
     * Builds a numbered source listing for the intel report prompt.
     */
    private String buildSourceListing(List<SourceCitation> citations) {
        log.debug("buildSourceListing() | citationCount={}", citations.size());

        StringBuilder sb = new StringBuilder();
        for (SourceCitation citation : citations) {
            sb.append("[").append(citation.citationNumber()).append("] ")
              .append(citation.title()).append("\n")
              .append("URL: ").append(citation.url()).append("\n\n");
        }

        String result = sb.toString().trim();
        log.debug("buildSourceListing() | return={} chars", result.length());
        return result;
    }

    /**
     * Strips markdown code fences (```html ... ```) that LLMs sometimes wrap around HTML output.
     */
    private String stripMarkdownFences(String raw) {
        log.debug("stripMarkdownFences() | inputLength={}", raw == null ? 0 : raw.length());
        if (raw == null) {
            log.debug("stripMarkdownFences() | return=empty (null input)");
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("```html")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        String result = trimmed.trim();
        log.debug("stripMarkdownFences() | return={} chars", result.length());
        return result;
    }
}

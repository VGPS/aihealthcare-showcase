package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchPlan;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort;
import com.wgblackmon.aihealthcare.domain.model.ResearchSection;
import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Domain service that converts a set of {@link RetrievedSource} documents and a
 * {@link ResearchPlan} into a structured {@link ResearchAnswer} by calling the AI model
 * with a synthesis prompt.
 *
 * <p>The synthesis prompt instructs the model to organise its response into 2-3 thematic
 * sections, each introduced by a {@code ##} Markdown heading and containing inline
 * citation markers ({@code [1]}, {@code [2]}, …).  This service then parses those
 * headings to split the raw AI response into individual {@link ResearchSection} records.
 *
 * <p>When the AI response contains no {@code ##} headings the entire response is placed
 * in a single section titled "Research Findings".
 *
 * <p>The synthesis prompt template is loaded at construction time via
 * {@code PromptLoaderService} and injected through {@code AppConfig}.  Placeholders
 * substituted: {@code {query}}, {@code {sources}}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
@Slf4j
public class ResearchSynthesisService {

    private final AiReportPort aiReportPort;
    private final String       synthesisPromptTemplate;

    /**
     * Constructs the service with its AI port and pre-loaded synthesis prompt template.
     *
     * @param aiReportPort              Port for sending prompts to the configured LLM.
     * @param synthesisPromptTemplate   Template from {@code research-synthesis.txt}; must
     *                                  contain {@code {query}} and {@code {sources}} placeholders.
     */
    public ResearchSynthesisService(AiReportPort aiReportPort, String synthesisPromptTemplate) {
        log.debug("ResearchSynthesisService() | aiReportPort={}, templateLength={}",
                  aiReportPort.getClass().getSimpleName(),
                  synthesisPromptTemplate == null ? 0 : synthesisPromptTemplate.length());
        this.aiReportPort             = aiReportPort;
        this.synthesisPromptTemplate  = synthesisPromptTemplate;
        log.debug("ResearchSynthesisService() | return=void");
    }

    /**
     * Synthesize a {@link ResearchAnswer} from the retrieved sources and research plan.
     *
     * @param query       The original research question.
     * @param plan        The research plan used to retrieve sources (used for context).
     * @param sources     All sources collected across sub-queries; may be empty.
     * @param citations   Pre-assembled, deduplicated citation list for cross-referencing.
     * @return A structured answer with 1–3 sections; never {@code null}.
     */
    public ResearchAnswer synthesize(String query,
                                     ResearchPlan plan,
                                     List<RetrievedSource> sources,
                                     List<SourceCitation> citations) {
        log.debug("synthesize() | query={}, sourceCount={}, citationCount={}",
                  query, sources == null ? 0 : sources.size(),
                  citations == null ? 0 : citations.size());

        if (sources == null || sources.isEmpty()) {
            log.warn("synthesize() | No sources available — returning empty-sources answer");
            ResearchAnswer result = buildEmptyAnswer(query, citations);
            log.debug("synthesize() | return=ResearchAnswer[sections=1, citations=0]");
            return result;
        }

        String sourceListing = buildSourceListing(sources, citations);
        String prompt = synthesisPromptTemplate
                .replace("{query}", query)
                .replace("{sources}", sourceListing);

        log.info("synthesize() | sending synthesis prompt to AI ({} chars)", prompt.length());
        String aiResponse = aiReportPort.generate(prompt);
        log.info("synthesize() | AI synthesis response received ({} chars)", aiResponse.length());

        List<ResearchSection> sections = parseSections(aiResponse, citations);

        ResearchAnswer result = new ResearchAnswer(
                UUID.randomUUID().toString(),
                query,
                sections,
                citations == null ? new ArrayList<>() : citations,
                Instant.now()
        );

        log.debug("synthesize() | return=ResearchAnswer[sections={}, citations={}]",
                  result.sections().size(), result.allCitations().size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Build a numbered source listing for the synthesis prompt. */
    private String buildSourceListing(List<RetrievedSource> sources,
                                      List<SourceCitation> citations) {
        log.debug("buildSourceListing() | sourceCount={}", sources.size());

        StringBuilder sb = new StringBuilder();
        for (SourceCitation citation : citations) {
            // Find matching source for the snippet
            String snippet = "";
            for (RetrievedSource source : sources) {
                String key = (source.url() != null && !source.url().isBlank())
                        ? source.url() : source.sourceId();
                if (key.equals(citation.url()) || source.sourceId().equals(citation.url())) {
                    snippet = source.snippet() != null ? source.snippet() : "";
                    break;
                }
            }
            sb.append("[").append(citation.citationNumber()).append("] ")
              .append(citation.title()).append("\n")
              .append("URL: ").append(citation.url()).append("\n")
              .append("Excerpt: ").append(snippet, 0, Math.min(snippet.length(), 500))
              .append("\n\n");
        }

        String result = sb.toString().trim();
        log.debug("buildSourceListing() | return={} chars", result.length());
        return result;
    }

    /**
     * Parse the AI response into sections by splitting on {@code ##} Markdown headings.
     * Falls back to a single "Research Findings" section when no headings are found.
     */
    private List<ResearchSection> parseSections(String response,
                                                 List<SourceCitation> citations) {
        log.debug("parseSections() | responseLength={}", response == null ? 0 : response.length());

        List<ResearchSection> result = new ArrayList<>();

        if (response == null || response.isBlank()) {
            result.add(new ResearchSection("Research Findings",
                    "No content was returned by the AI model.",
                    new ArrayList<>()));
            log.debug("parseSections() | return=1 section (empty response)");
            return result;
        }

        // Split on lines that begin with ## (Markdown heading level 2)
        String[] parts = response.split("(?m)^##\\s+");

        if (parts.length <= 1) {
            // No ## headings found — wrap entire response in one section
            result.add(new ResearchSection(
                    "Research Findings",
                    response.trim(),
                    citations != null ? citations : new ArrayList<>()));
            log.debug("parseSections() | return=1 section (no headings)");
            return result;
        }

        // parts[0] is pre-heading preamble — skip it if blank
        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int newline = part.indexOf('\n');
            if (newline < 0) {
                result.add(new ResearchSection(part.trim(), "", new ArrayList<>()));
                continue;
            }
            String heading = part.substring(0, newline).trim();
            String body    = part.substring(newline).trim();
            // Associate all citations with each section (full list for simplicity)
            result.add(new ResearchSection(
                    heading,
                    body,
                    citations != null ? citations : new ArrayList<>()));
        }

        if (result.isEmpty()) {
            result.add(new ResearchSection("Research Findings",
                    response.trim(), new ArrayList<>()));
        }

        log.debug("parseSections() | return={} sections", result.size());
        return result;
    }

    /** Build a minimal answer when no sources are available. */
    private ResearchAnswer buildEmptyAnswer(String query, List<SourceCitation> citations) {
        log.debug("buildEmptyAnswer() | query={}", query);
        List<ResearchSection> sections = Collections.singletonList(
                new ResearchSection(
                        "Research Findings",
                        "No sources were available for this query. "
                        + "Try switching to STAGED_RESEARCH mode or check your retrieval configuration.",
                        new ArrayList<>()));
        ResearchAnswer result = new ResearchAnswer(
                UUID.randomUUID().toString(),
                query,
                sections,
                citations != null ? citations : new ArrayList<>(),
                Instant.now());
        log.debug("buildEmptyAnswer() | return=ResearchAnswer[sections=1]");
        return result;
    }
}

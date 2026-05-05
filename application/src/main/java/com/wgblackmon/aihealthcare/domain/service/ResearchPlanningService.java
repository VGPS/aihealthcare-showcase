package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ResearchPlan;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiReportPort;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Domain service that decomposes a research query into a structured {@link ResearchPlan}
 * by calling the AI model with a planning prompt.
 *
 * <p>The planning prompt asks the model to produce a short list of focused sub-queries
 * and a rationale sentence.  The expected response format is:
 * <pre>
 * RATIONALE: &lt;one sentence&gt;
 * SUB-QUERIES:
 * 1. &lt;first sub-query&gt;
 * 2. &lt;second sub-query&gt;
 * ...
 * </pre>
 *
 * <p>If the model's response cannot be parsed (e.g., it returns free-form text or is
 * empty), the service falls back gracefully to a single-entry plan that contains the
 * original query verbatim.  This ensures the pipeline always makes progress even when
 * the planning step is uncertain.
 *
 * <p>The prompt template is loaded at construction time via
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService} and
 * injected through {@code AppConfig}.  Two placeholders are substituted:
 * {@code {query}} and {@code {topicHint}}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
@Slf4j
public class ResearchPlanningService {

    private final AiReportPort aiReportPort;
    private final String       planPromptTemplate;

    /**
     * Constructs the service with its AI port and the pre-loaded planning prompt template.
     *
     * @param aiReportPort       Port for sending prompts to the configured LLM.
     * @param planPromptTemplate Template text loaded from {@code research-plan.txt};
     *                           must contain {@code {query}} and {@code {topicHint}} placeholders.
     */
    public ResearchPlanningService(AiReportPort aiReportPort, String planPromptTemplate) {
        log.debug("ResearchPlanningService() | aiReportPort={}, templateLength={}",
                  aiReportPort.getClass().getSimpleName(),
                  planPromptTemplate == null ? 0 : planPromptTemplate.length());
        this.aiReportPort       = aiReportPort;
        this.planPromptTemplate = planPromptTemplate;
        log.debug("ResearchPlanningService() | return=void");
    }

    /**
     * Decompose {@code query} into a {@link ResearchPlan} with focused sub-queries.
     *
     * @param query      The original research question; must not be blank.
     * @param topicHint  Optional narrowing hint (e.g., "clinical AI"); may be blank.
     * @return A plan containing at least one sub-query; never {@code null}.
     */
    public ResearchPlan plan(String query, String topicHint) {
        log.debug("plan() | query={}, topicHint={}", query, topicHint);

        String hint = (topicHint != null && !topicHint.isBlank()) ? topicHint : "general";
        String prompt = planPromptTemplate
                .replace("{query}", query)
                .replace("{topicHint}", hint);

        log.info("plan() | sending planning prompt to AI ({} chars)", prompt.length());
        String aiResponse = aiReportPort.generate(prompt);
        log.info("plan() | AI planning response received ({} chars)", aiResponse.length());

        List<String> subQueries = parseSubQueries(aiResponse);
        String rationale        = parseRationale(aiResponse);

        if (subQueries.isEmpty()) {
            log.warn("plan() | Could not parse sub-queries from AI response — using original query as fallback");
            subQueries.add(query);
            rationale = "Fallback: AI response could not be parsed; using original query.";
        }

        ResearchPlan result = new ResearchPlan(
                UUID.randomUUID().toString(),
                query,
                subQueries,
                rationale,
                subQueries.size() * 10   // rough estimate: 10 sources per sub-query
        );

        log.debug("plan() | return=ResearchPlan[subQueryCount={}]", result.subQueries().size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Private parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Extract numbered sub-query lines from the AI response.
     * Matches lines that begin with a digit, a period or closing parenthesis, and a space.
     */
    private List<String> parseSubQueries(String response) {
        log.debug("parseSubQueries() | responseLength={}", response == null ? 0 : response.length());

        List<String> result = new ArrayList<>();
        if (response == null || response.isBlank()) {
            log.debug("parseSubQueries() | return=[] (empty response)");
            return result;
        }

        String[] lines = response.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches("^\\d+[.)].+")) {
                // Strip the leading "1. " or "1) " prefix
                String subQuery = trimmed.replaceFirst("^\\d+[.)\\s]+", "").trim();
                if (!subQuery.isBlank()) {
                    result.add(subQuery);
                }
            }
        }

        log.debug("parseSubQueries() | return={} sub-queries", result.size());
        return result;
    }

    /**
     * Extract the RATIONALE line from the AI response, returning a default if absent.
     */
    private String parseRationale(String response) {
        log.debug("parseRationale() | responseLength={}", response == null ? 0 : response.length());

        if (response == null || response.isBlank()) {
            log.debug("parseRationale() | return=default");
            return "No rationale provided.";
        }

        String[] lines = response.split("\\r?\\n");
        for (String line : lines) {
            String upper = line.trim().toUpperCase();
            if (upper.startsWith("RATIONALE:")) {
                String result = line.trim().substring("RATIONALE:".length()).trim();
                log.debug("parseRationale() | return={}", result);
                return result.isBlank() ? "No rationale provided." : result;
            }
        }

        log.debug("parseRationale() | return=default (no RATIONALE line found)");
        return "No rationale provided.";
    }
}

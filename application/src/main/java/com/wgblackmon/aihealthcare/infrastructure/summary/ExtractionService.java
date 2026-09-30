package com.wgblackmon.aihealthcare.infrastructure.summary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Stage 1 of the anti-slop pipeline: extracts structured facts from source documents.
 *
 * <p>Calls the configured LLM with the extraction prompt and the rendered source block,
 * then parses the JSON response into a {@link SummaryExtraction}.  Validates that every
 * finding has at least one source ID and that all cited IDs exist in the source list.
 *
 * <p>On validation failure, retries once by appending the error message to the prompt.
 * On a second failure, throws {@link ExtractionValidationException} so the pipeline
 * orchestrator can fall back to the legacy summarizer.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-29
 * @updated 2026-09-29
 */
@Slf4j
@Component
public class ExtractionService {

    private static final String DEFAULT_TYPE_NOTES =
            "TOPIC_SUMMARY: emphasize key facts, named companies or systems, measurable outcomes, and regulatory or funding events.";

    private final ChatClient         chatClient;
    private final PromptLoaderService promptLoader;
    private final ObjectMapper        mapper;

    public ExtractionService(ChatClient.Builder chatClientBuilder,
                              PromptLoaderService promptLoader,
                              ObjectMapper mapper) {
        this.chatClient   = chatClientBuilder.build();
        this.promptLoader = promptLoader;
        this.mapper       = mapper;
    }

    public SummaryExtraction extract(String summaryType,
                                     String topic,
                                     List<SourceDoc> sources) {
        log.debug("extract() | summaryType={}, topic={}, sourceCount={}", summaryType, topic, sources.size());

        String systemTemplate = promptLoader.load("extract-system.txt");
        String sourceBlock    = SourceRenderer.render(sources);
        String systemPrompt   = systemTemplate
                .replace("{summaryType}", summaryType)
                .replace("{typeNotes}", DEFAULT_TYPE_NOTES);

        String userPrompt = "Topic: " + topic + "\n\nSources:\n" + sourceBlock;
        String fullPrompt = systemPrompt + "\n\n" + userPrompt;

        SummaryExtraction result = callAndParse(fullPrompt, sources, null);
        log.debug("extract() | return={}", result);
        return result;
    }

    private SummaryExtraction callAndParse(String prompt,
                                           List<SourceDoc> sources,
                                           String priorError) {
        String effectivePrompt = priorError == null
                ? prompt
                : prompt + "\n\nYour previous response was invalid: " + priorError
                        + "\nFix only that issue and return valid JSON.";

        String raw = chatClient.prompt(effectivePrompt).call().content();
        log.debug("callAndParse() | raw response length={}", raw == null ? 0 : raw.length());

        String json = extractJson(raw);
        try {
            SummaryExtraction extraction = mapper.readValue(json, SummaryExtraction.class);
            validate(extraction, sources);
            return extraction;
        } catch (Exception e) {
            if (priorError != null) {
                throw new ExtractionValidationException("Extraction failed after retry: " + e.getMessage(), e);
            }
            log.warn("extract() | first attempt failed ({}), retrying", e.getMessage());
            return callAndParse(prompt, sources, e.getMessage());
        }
    }

    /** Pulls the JSON object out of a response that may have surrounding prose. */
    private static String extractJson(String raw) {
        if (raw == null) throw new ExtractionValidationException("Null response from model");
        int start = raw.indexOf('{');
        int end   = raw.lastIndexOf('}');
        if (start < 0 || end < 0) throw new ExtractionValidationException("No JSON object in response");
        return raw.substring(start, end + 1);
    }

    static void validate(SummaryExtraction x, List<SourceDoc> sources) {
        if (x == null) throw new ExtractionValidationException("Null extraction");
        if (x.headline() == null || x.headline().isBlank())
            throw new ExtractionValidationException("Missing headline");
        if (x.findings() == null || x.findings().isEmpty())
            throw new ExtractionValidationException("Missing findings");

        Set<String> validIds = sources.stream()
                .map(SourceDoc::citeId)
                .collect(Collectors.toSet());

        for (SummaryExtraction.Finding f : x.findings()) {
            if (f.sourceIds() == null || f.sourceIds().isEmpty())
                throw new ExtractionValidationException("Finding without sourceIds: " + f.statement());
            if (!validIds.isEmpty()) {
                for (String id : f.sourceIds()) {
                    if (!validIds.contains(id))
                        throw new ExtractionValidationException("Unknown sourceId " + id + " in finding");
                }
            }
        }
    }
}

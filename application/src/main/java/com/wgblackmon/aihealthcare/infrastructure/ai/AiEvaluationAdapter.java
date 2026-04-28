package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.EvaluationScore;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiEvaluationPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI adapter that implements {@link AiEvaluationPort} using an LLM-as-judge
 * evaluation pattern.
 *
 * <p>This adapter sends a dedicated evaluation prompt to the LLM, instructing it to
 * act as a quality judge that scores an AI-generated newsletter section against the
 * original source articles on five dimensions: relevance, conciseness, attribution
 * quality, tone match, and completeness.
 *
 * <p>The evaluation prompt is loaded from {@code /prompts/evaluate-section.txt} on the
 * classpath.  The LLM's response is parsed from a strict line-based format into an
 * {@link EvaluationScore} domain record.  If the LLM deviates from the expected format,
 * sensible defaults (0.5 for missing scores, empty notes) are used so the pipeline
 * degrades gracefully.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-04-18
 * @updated 2026-04-28
 */
@Slf4j
@Component
public class AiEvaluationAdapter implements AiEvaluationPort {

    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;

    public AiEvaluationAdapter(ChatClient.Builder chatClientBuilder,
                                PromptLoaderService promptLoaderService) {
        log.debug("AiEvaluationAdapter() | chatClientBuilder={}, promptLoaderService={}",
                  chatClientBuilder, promptLoaderService.getClass().getSimpleName());
        this.chatClient = chatClientBuilder.build();
        this.promptLoaderService = promptLoaderService;
        log.debug("AiEvaluationAdapter() | return=void");
    }

    @Override
    public EvaluationScore evaluate(NewsletterSection section,
                                     List<NewsArticle> articles,
                                     String topic,
                                     NewsletterTone tone) {
        log.debug("evaluate() | sectionId={}, topic={}, tone={}, articleCount={}",
                  section.sectionId(), topic, tone, articles.size());

        String prompt = buildEvaluationPrompt(section, articles, topic, tone);
        log.debug("evaluate() | sending prompt to LLM, length={} chars", prompt.length());

        String response = chatClient.prompt(prompt).call().content();
        log.debug("evaluate() | received LLM response, length={} chars", response.length());

        EvaluationScore result = parseScore(response);
        log.info("evaluate() | Section scored: sectionId={}, overall={}", section.sectionId(), result.overall());
        log.debug("evaluate() | return={}", result);
        return result;
    }

    // -------------------------------------------------------------------------
    // Prompt builder
    // -------------------------------------------------------------------------

    private String buildEvaluationPrompt(NewsletterSection section,
                                          List<NewsArticle> articles,
                                          String topic,
                                          NewsletterTone tone) {
        log.debug("buildEvaluationPrompt() | topic={}, tone={}, articleCount={}", topic, tone, articles.size());

        StringBuilder articlesBlock = new StringBuilder();
        int index = 1;
        for (NewsArticle article : articles) {
            articlesBlock.append("[").append(index).append("] Title: ").append(article.title()).append("\n");
            if (article.author() != null && !article.author().isBlank()) {
                articlesBlock.append("    By:    ").append(article.author()).append("\n");
            }
            articlesBlock.append("    Body:  ").append(article.bodyText()).append("\n\n");
            index++;
        }

        String template = promptLoaderService.load("evaluate-section.txt");
        String result = template
                .replace("{topic}", topic)
                .replace("{tone}", tone.name())
                .replace("{headline}", section.headline())
                .replace("{summary}", section.summary())
                .replace("{sectionType}", section.sectionType().name())
                .replace("{articles}", articlesBlock.toString().trim());

        log.debug("buildEvaluationPrompt() | return=prompt[{} chars]", result.length());
        return result;
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    /**
     * Parses the LLM's structured response into an {@link EvaluationScore}.
     *
     * <p>Expected format:
     * <pre>
     * RELEVANCE: 0.85
     * CONCISENESS: 0.90
     * ATTRIBUTION_QUALITY: 0.75
     * TONE_MATCH: 0.80
     * COMPLETENESS: 0.70
     * OVERALL: 0.80
     * NOTES: Brief explanation of scores.
     * </pre>
     *
     * Missing or unparseable scores default to 0.5.
     */
    EvaluationScore parseScore(String response) {
        log.debug("parseScore() | responseLength={}", response.length());

        double relevance = 0.5;
        double conciseness = 0.5;
        double attributionQuality = 0.5;
        double toneMatch = 0.5;
        double completeness = 0.5;
        double overall = 0.5;
        String notes = "";

        for (String line : response.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("RELEVANCE:")) {
                relevance = parseDouble(trimmed.substring("RELEVANCE:".length()).trim(), "RELEVANCE");
            } else if (trimmed.startsWith("CONCISENESS:")) {
                conciseness = parseDouble(trimmed.substring("CONCISENESS:".length()).trim(), "CONCISENESS");
            } else if (trimmed.startsWith("ATTRIBUTION_QUALITY:")) {
                attributionQuality = parseDouble(trimmed.substring("ATTRIBUTION_QUALITY:".length()).trim(), "ATTRIBUTION_QUALITY");
            } else if (trimmed.startsWith("TONE_MATCH:")) {
                toneMatch = parseDouble(trimmed.substring("TONE_MATCH:".length()).trim(), "TONE_MATCH");
            } else if (trimmed.startsWith("COMPLETENESS:")) {
                completeness = parseDouble(trimmed.substring("COMPLETENESS:".length()).trim(), "COMPLETENESS");
            } else if (trimmed.startsWith("OVERALL:")) {
                overall = parseDouble(trimmed.substring("OVERALL:".length()).trim(), "OVERALL");
            } else if (trimmed.startsWith("NOTES:")) {
                notes = trimmed.substring("NOTES:".length()).trim();
            }
        }

        EvaluationScore result = new EvaluationScore(
                relevance, conciseness, attributionQuality, toneMatch, completeness, overall, notes);
        log.debug("parseScore() | return={}", result);
        return result;
    }

    /**
     * Parses a double value from a string, clamping to [0.0, 1.0].
     * Returns 0.5 on parse failure.
     */
    private double parseDouble(String value, String fieldName) {
        log.debug("parseDouble() | value={}, fieldName={}", value, fieldName);
        try {
            double parsed = Double.parseDouble(value);
            if (parsed < 0.0) {
                log.warn("parseDouble() | {} value {} below 0.0, clamping to 0.0", fieldName, parsed);
                parsed = 0.0;
            } else if (parsed > 1.0) {
                log.warn("parseDouble() | {} value {} above 1.0, clamping to 1.0", fieldName, parsed);
                parsed = 1.0;
            }
            log.debug("parseDouble() | return={}", parsed);
            return parsed;
        } catch (NumberFormatException e) {
            log.warn("parseDouble() | Failed to parse {} value '{}', defaulting to 0.5", fieldName, value);
            log.debug("parseDouble() | return=0.5");
            return 0.5;
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

}

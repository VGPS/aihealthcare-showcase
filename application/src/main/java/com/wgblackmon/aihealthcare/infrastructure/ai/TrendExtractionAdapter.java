package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.ExtractedTrend;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendTopicExtractionPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI adapter that implements {@link TrendTopicExtractionPort} using a
 * large-language model to identify emerging trend topics from article titles.
 *
 * <p>Sends a numbered list of recent article titles to the LLM with a prompt
 * asking it to identify significant healthcare AI themes. Parses the structured
 * response to extract trend labels, descriptions, and linked article indices.
 *
 * <p>The prompt template is loaded from {@code prompts/trend-extract.txt}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-29
 * @updated 2026-07-29
 */
@Slf4j
@Component
public class TrendExtractionAdapter implements TrendTopicExtractionPort {

    private final ChatClient chatClient;
    private final PromptLoaderService promptLoaderService;

    /**
     * Constructs the adapter with a ChatClient builder and prompt loader.
     *
     * @param chatClientBuilder   auto-configured builder from Spring AI
     * @param promptLoaderService service to load prompt templates
     */
    public TrendExtractionAdapter(ChatClient.Builder chatClientBuilder,
                                   PromptLoaderService promptLoaderService) {
        log.debug("TrendExtractionAdapter() | chatClientBuilder={}, promptLoaderService={}",
                  chatClientBuilder, promptLoaderService.getClass().getSimpleName());
        this.chatClient = chatClientBuilder.build();
        this.promptLoaderService = promptLoaderService;
        log.debug("TrendExtractionAdapter() | return=void");
    }

    @Override
    public List<ExtractedTrend> extractTopics(List<String> articleTitles, int maxTopics) {
        log.debug("extractTopics() | articleCount={}, maxTopics={}",
                  articleTitles != null ? articleTitles.size() : 0, maxTopics);

        if (articleTitles == null || articleTitles.isEmpty()) {
            log.debug("extractTopics() | return=[] (empty input)");
            return List.of();
        }

        String prompt = buildPrompt(articleTitles, maxTopics);
        log.debug("extractTopics() | prompt length={} chars", prompt.length());

        String response;
        try {
            response = chatClient.prompt(prompt).call().content();
        } catch (Exception e) {
            log.warn("extractTopics() | LLM call failed: {}", e.getMessage());
            log.debug("extractTopics() | return=[] (LLM error)");
            return List.of();
        }

        if (response == null || response.isBlank()) {
            log.warn("extractTopics() | Empty LLM response");
            log.debug("extractTopics() | return=[] (empty response)");
            return List.of();
        }

        log.debug("extractTopics() | LLM response length={} chars", response.length());
        List<ExtractedTrend> result = parseResponse(response, articleTitles.size());
        log.info("extractTopics() | extracted {} trends from {} articles", result.size(), articleTitles.size());
        log.debug("extractTopics() | return={}", result);
        return result;
    }

    /**
     * Builds the extraction prompt from the template and numbered article titles.
     */
    String buildPrompt(List<String> articleTitles, int maxTopics) {
        log.debug("buildPrompt() | articleCount={}, maxTopics={}", articleTitles.size(), maxTopics);

        String template = promptLoaderService.load("trend-extract.txt");

        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < articleTitles.size(); i++) {
            numbered.append(i + 1).append(". ").append(articleTitles.get(i)).append("\n");
        }

        String result = template
                .replace("{articleCount}", String.valueOf(articleTitles.size()))
                .replace("{maxTopics}", String.valueOf(maxTopics))
                .replace("{numberedTitles}", numbered.toString().trim());

        log.debug("buildPrompt() | return=prompt ({} chars)", result.length());
        return result;
    }

    /**
     * Parses the LLM response to extract trend topics.
     *
     * <p>Expected format per trend block:
     * <pre>
     * TREND:
     * LABEL: &lt;theme label&gt;
     * DESCRIPTION: &lt;one sentence&gt;
     * ARTICLES: 1, 5, 12, 23
     * </pre>
     */
    List<ExtractedTrend> parseResponse(String response, int totalArticles) {
        log.debug("parseResponse() | responseLength={}, totalArticles={}", response.length(), totalArticles);

        List<ExtractedTrend> trends = new ArrayList<>();

        String currentLabel = null;
        String currentDescription = null;
        List<Integer> currentIndices = null;

        String[] lines = response.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.startsWith("TREND:")) {
                // Save previous trend if complete
                if (currentLabel != null && currentDescription != null) {
                    trends.add(new ExtractedTrend(currentLabel, currentDescription,
                            currentIndices != null ? currentIndices : List.of()));
                }
                currentLabel = null;
                currentDescription = null;
                currentIndices = null;
                continue;
            }

            if (trimmed.startsWith("LABEL:")) {
                currentLabel = trimmed.substring(6).trim();
                if (currentLabel.isEmpty()) {
                    currentLabel = null;
                }
                continue;
            }

            if (trimmed.startsWith("DESCRIPTION:")) {
                currentDescription = trimmed.substring(12).trim();
                if (currentDescription.isEmpty()) {
                    currentDescription = null;
                }
                continue;
            }

            if (trimmed.startsWith("ARTICLES:")) {
                currentIndices = parseArticleIndices(trimmed.substring(9).trim(), totalArticles);
                continue;
            }
        }

        // Save last trend if complete
        if (currentLabel != null && currentDescription != null) {
            trends.add(new ExtractedTrend(currentLabel, currentDescription,
                    currentIndices != null ? currentIndices : List.of()));
        }

        log.debug("parseResponse() | return={} trends", trends.size());
        return trends;
    }

    /**
     * Parses comma-separated article numbers (1-based) into zero-based indices.
     */
    private List<Integer> parseArticleIndices(String indicesStr, int totalArticles) {
        log.debug("parseArticleIndices() | indicesStr={}, totalArticles={}", indicesStr, totalArticles);

        List<Integer> indices = new ArrayList<>();
        String[] parts = indicesStr.split(",");

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                int oneBasedIndex = Integer.parseInt(trimmed);
                int zeroBasedIndex = oneBasedIndex - 1;
                if (zeroBasedIndex >= 0 && zeroBasedIndex < totalArticles) {
                    indices.add(zeroBasedIndex);
                } else {
                    log.warn("parseArticleIndices() | index {} out of range (total={})",
                              oneBasedIndex, totalArticles);
                }
            } catch (NumberFormatException e) {
                log.warn("parseArticleIndices() | failed to parse index: '{}'", trimmed);
            }
        }

        log.debug("parseArticleIndices() | return={} indices", indices.size());
        return indices;
    }
}

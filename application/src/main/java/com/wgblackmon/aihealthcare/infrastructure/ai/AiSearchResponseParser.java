package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared utility for parsing AI search synthesis responses and building
 * prompts for the AI search synthesis pipeline.
 *
 * <p>Extracts the duplicated {@code parseResponse()} and {@code buildPrompt()}
 * logic that was previously repeated across all four AI search adapters
 * ({@link AnthropicAiSearchAdapter}, {@link OpenAiSearchAdapter},
 * {@link PerplexityAiSearchAdapter}, {@link GeminiAiSearchAdapter}) into a
 * single reusable component. Each adapter passes its own model name to
 * parameterize log messages and the resulting {@link AiSearchSynthesis} record.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-11
 * @updated 2026-09-11
 */
@Slf4j
@Component
public class AiSearchResponseParser {

    private final PromptLoaderService promptLoaderService;

    /**
     * Constructs the parser with the prompt loader used to resolve the
     * {@code ai-search-synthesis.txt} template.
     *
     * @param promptLoaderService service for loading prompt templates
     */
    public AiSearchResponseParser(PromptLoaderService promptLoaderService) {
        log.debug("AiSearchResponseParser() | promptLoaderService={}",
                  promptLoaderService.getClass().getSimpleName());
        this.promptLoaderService = promptLoaderService;
        log.debug("AiSearchResponseParser() | return=void");
    }

    /**
     * Parses a raw LLM response into a structured {@link AiSearchSynthesis}.
     *
     * <p>Handles three cases: null/blank response (fallback message),
     * {@code NO_MATCH} prefix (returns null to signal irrelevance), and
     * normal structured response with {@code SUMMARY:} and
     * {@code KEY_FINDINGS:} sections.
     *
     * @param response  the raw text response from the LLM (may be null)
     * @param modelName the display name of the model (e.g. "Claude", "GPT")
     * @return parsed synthesis, or null if the model reported NO_MATCH
     */
    public AiSearchSynthesis parseResponse(String response, String modelName) {
        log.debug("parseResponse() | responseLength={}, modelName={}",
                  response == null ? 0 : response.length(), modelName);

        if (response == null || response.isBlank()) {
            log.warn("parseResponse() | empty response from {}", modelName);
            AiSearchSynthesis result = new AiSearchSynthesis(
                    modelName, "No synthesis available.", new ArrayList<>(), Instant.now());
            log.debug("parseResponse() | return={}", result);
            return result;
        }

        if (response.trim().startsWith("NO_MATCH")) {
            log.info("parseResponse() | {} reported NO_MATCH — articles not relevant to query", modelName);
            log.debug("parseResponse() | return=null");
            return null;
        }

        StringBuilder summaryBuilder = new StringBuilder();
        List<String> keyFindings = new ArrayList<>();
        boolean inSummary = false;
        boolean inFindings = false;

        for (String line : response.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.startsWith("SUMMARY:")) {
                summaryBuilder.append(trimmed.substring("SUMMARY:".length()).trim());
                inSummary = true;
                inFindings = false;
            } else if (trimmed.equals("KEY_FINDINGS:")) {
                inSummary = false;
                inFindings = true;
            } else if (inSummary && !trimmed.isEmpty()) {
                summaryBuilder.append(" ").append(trimmed);
            } else if (inFindings && trimmed.startsWith("- ")) {
                keyFindings.add(trimmed.substring(2).trim());
            } else if (inFindings && trimmed.startsWith("* ")) {
                keyFindings.add(trimmed.substring(2).trim());
            }
        }

        String summary = summaryBuilder.toString().trim();
        if (summary.isEmpty()) {
            log.warn("parseResponse() | SUMMARY line not found in {} response; using full response as summary", modelName);
            summary = response.length() > 4000 ? response.substring(0, 4000) + "..." : response;
        }

        AiSearchSynthesis result = new AiSearchSynthesis(modelName, summary, keyFindings, Instant.now());
        log.debug("parseResponse() | return=AiSearchSynthesis[findings={}]", keyFindings.size());
        return result;
    }

    /**
     * Builds a formatted prompt from the query and article list using the
     * {@code ai-search-synthesis.txt} template.
     *
     * <p>Each article is formatted with numbered references ({@code [N]}),
     * title, optional author/source, and body text truncated to 500 characters.
     *
     * @param query    the user's search query
     * @param articles the retrieved articles to include in the prompt
     * @return the fully assembled prompt string
     */
    public String buildPrompt(String query, List<NewsArticle> articles) {
        log.debug("buildPrompt() | query={}, articleCount={}", query, articles.size());

        StringBuilder articlesBlock = new StringBuilder();
        int index = 1;
        for (NewsArticle article : articles) {
            articlesBlock.append("[").append(index).append("] Title: ").append(article.title()).append("\n");
            if (article.author() != null && !article.author().isBlank()) {
                articlesBlock.append("    Author: ").append(article.author()).append("\n");
            }
            if (article.sourceName() != null && !article.sourceName().isBlank()) {
                articlesBlock.append("    Source: ").append(article.sourceName()).append("\n");
            }
            String body = article.bodyText() != null ? article.bodyText() : "";
            if (body.length() > 500) {
                body = body.substring(0, 500) + "...";
            }
            articlesBlock.append("    Body:   ").append(body).append("\n\n");
            index++;
        }

        String template = promptLoaderService.load("ai-search-synthesis.txt");
        String result = template
                .replace("{query}", query)
                .replace("{articleCount}", String.valueOf(articles.size()))
                .replace("{articles}", articlesBlock.toString().trim());

        log.debug("buildPrompt() | return=prompt[{} chars]", result.length());
        return result;
    }
}

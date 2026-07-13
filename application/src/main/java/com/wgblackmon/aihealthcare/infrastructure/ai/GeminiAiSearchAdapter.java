package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google Gemini adapter for AI-enhanced search synthesis.
 *
 * <p>Implements {@link AiSearchPort} using the Google Gemini REST API
 * ({@code POST https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent})
 * via {@link RestClient}.  Like the Perplexity adapter, Gemini does not have
 * a Spring AI starter — so this adapter calls the REST API directly.
 *
 * <p>Shares the same prompt template ({@code ai-search-synthesis.txt}) and
 * response parsing logic as the other search adapters, enabling direct
 * side-by-side comparison of model outputs across all four providers.
 *
 * <p>When {@code GEMINI_API_KEY} is not set, the adapter returns a
 * graceful fallback synthesis rather than throwing an exception.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-10
 */
@Slf4j
@Component
public class GeminiAiSearchAdapter implements AiSearchPort {

    private static final String MODEL_NAME = "Gemini";
    private static final String BASE_URL   = "https://generativelanguage.googleapis.com";

    private final PromptLoaderService promptLoaderService;
    private final String              apiKey;
    private final String              modelId;
    private final RestClient          restClient;

    /**
     * Spring-managed constructor — builds a {@link RestClient} targeting the
     * Google Generative AI base URL.  API key injected from
     * {@code aihealthcare.gemini.api-key} (resolved from
     * {@code GEMINI_API_KEY} env var; defaults to blank string when absent).
     *
     * @param promptLoaderService service for loading prompt templates
     * @param apiKey              Gemini API key; blank when env var is not set
     */
    @Autowired
    public GeminiAiSearchAdapter(
            PromptLoaderService promptLoaderService,
            @Value("${aihealthcare.gemini.api-key:}") String apiKey,
            @Value("${aihealthcare.gemini.model:gemini-2.0-flash}") String modelId) {
        this(promptLoaderService, apiKey, modelId, RestClient.builder().baseUrl(BASE_URL).build());
    }

    /**
     * Package-private constructor for unit testing — accepts a pre-built
     * {@link RestClient} so HTTP calls can be intercepted by a mock.
     *
     * @param promptLoaderService service for loading prompt templates
     * @param apiKey              Gemini API key (may be blank to test guard path)
     * @param restClient          pre-configured RestClient (injected by tests)
     */
    GeminiAiSearchAdapter(PromptLoaderService promptLoaderService,
                          String apiKey,
                          String modelId,
                          RestClient restClient) {
        log.debug("GeminiAiSearchAdapter() | promptLoaderService={}, apiKeyPresent={}, modelId={}",
                  promptLoaderService.getClass().getSimpleName(), apiKey != null && !apiKey.isBlank(), modelId);
        this.promptLoaderService = promptLoaderService;
        this.apiKey              = apiKey;
        this.modelId             = modelId;
        this.restClient          = restClient;
        if (apiKey == null || apiKey.isBlank()) {
            log.info("GeminiAiSearchAdapter() | GEMINI_API_KEY not set — adapter will return fallback synthesis");
        } else {
            log.info("GeminiAiSearchAdapter() | Gemini API active for AI search (model={})", modelId);
        }
        log.debug("GeminiAiSearchAdapter() | return=void");
    }

    @Override
    public AiSearchSynthesis synthesize(String query, List<NewsArticle> articles) {
        log.debug("synthesize() | query={}, articleCount={}", query, articles.size());

        if (apiKey == null || apiKey.isBlank()) {
            log.info("synthesize() | GEMINI_API_KEY not configured — returning fallback");
            AiSearchSynthesis result = new AiSearchSynthesis(
                    MODEL_NAME, "Gemini API key not configured.", new ArrayList<>(), Instant.now());
            log.debug("synthesize() | return={}", result);
            return result;
        }

        String prompt = buildPrompt(query, articles);
        log.info("synthesize() | sending prompt to Gemini ({} chars)", prompt.length());

        String response = callGeminiApi(prompt);
        log.info("synthesize() | received Gemini response ({} chars)",
                 response == null ? 0 : response.length());

        AiSearchSynthesis result = parseResponse(response);
        log.debug("synthesize() | return={}", result);
        return result;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    @Override
    public String modelId() {
        return modelId;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String callGeminiApi(String prompt) {
        log.debug("callGeminiApi() | promptLength={}", prompt.length());

        Map<String, String> textPart = new LinkedHashMap<>();
        textPart.put("text", prompt);

        List<Map<String, String>> parts = new ArrayList<>();
        parts.add(textPart);

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("parts", parts);

        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(content);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("contents", contents);

        GeminiApiResponse response = restClient.post()
                .uri("/v1beta/models/" + modelId + ":generateContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(GeminiApiResponse.class);

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            log.warn("callGeminiApi() | empty response from Gemini");
            log.debug("callGeminiApi() | return=null");
            return null;
        }

        GeminiApiResponse.GeminiCandidate candidate = response.candidates().get(0);
        if (candidate.content() == null || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            log.warn("callGeminiApi() | no content parts in Gemini response");
            log.debug("callGeminiApi() | return=null");
            return null;
        }

        String text = candidate.content().parts().get(0).text();
        log.debug("callGeminiApi() | return=content[{} chars]", text == null ? 0 : text.length());
        return text;
    }

    private String buildPrompt(String query, List<NewsArticle> articles) {
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

    private AiSearchSynthesis parseResponse(String response) {
        log.debug("parseResponse() | responseLength={}", response == null ? 0 : response.length());

        if (response == null || response.isBlank()) {
            log.warn("parseResponse() | empty response from Gemini");
            AiSearchSynthesis result = new AiSearchSynthesis(
                    MODEL_NAME, "No synthesis available.", new ArrayList<>(), Instant.now());
            log.debug("parseResponse() | return={}", result);
            return result;
        }

        if (response.trim().startsWith("NO_MATCH")) {
            log.info("parseResponse() | Gemini reported NO_MATCH — articles not relevant to query");
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
            log.warn("parseResponse() | SUMMARY line not found in Gemini response; using full response as summary");
            summary = response.length() > 4000 ? response.substring(0, 4000) + "..." : response;
        }

        AiSearchSynthesis result = new AiSearchSynthesis(MODEL_NAME, summary, keyFindings, Instant.now());
        log.debug("parseResponse() | return=AiSearchSynthesis[findings={}]", keyFindings.size());
        return result;
    }
}

package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity.PerplexityApiResponse;
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
 * Perplexity Sonar adapter for AI-enhanced search synthesis.
 *
 * <p>Implements {@link AiSearchPort} using the Perplexity Sonar API
 * ({@code POST https://api.perplexity.ai/chat/completions}) via {@link RestClient}.
 * Unlike the Claude and GPT adapters which use Spring AI's {@code ChatClient},
 * Perplexity does not have a Spring AI starter — so this adapter calls the
 * OpenAI-compatible REST API directly.
 *
 * <p>Shares the same prompt template ({@code ai-search-synthesis.txt}) and
 * response parsing logic as {@link AnthropicAiSearchAdapter} and
 * {@link OpenAiSearchAdapter}, enabling direct side-by-side comparison of
 * model outputs across all three providers.
 *
 * <p>When {@code PERPLEXITY_API_KEY} is not set, the adapter returns a
 * graceful fallback synthesis rather than throwing an exception.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-07-03
 */
@Slf4j
@Component
public class PerplexityAiSearchAdapter implements AiSearchPort {

    private static final String MODEL_NAME  = "Perplexity";
    private static final String BASE_URL    = "https://api.perplexity.ai";
    private static final String SONAR_MODEL = "sonar";

    private final PromptLoaderService promptLoaderService;
    private final String              apiKey;
    private final RestClient          restClient;

    /**
     * Spring-managed constructor — builds a {@link RestClient} targeting the
     * Perplexity base URL.  API key injected from
     * {@code aihealthcare.perplexity.api-key} (resolved from
     * {@code PERPLEXITY_API_KEY} env var; defaults to blank string when absent).
     *
     * @param promptLoaderService service for loading prompt templates
     * @param apiKey              Perplexity API key; blank when env var is not set
     */
    @Autowired
    public PerplexityAiSearchAdapter(
            PromptLoaderService promptLoaderService,
            @Value("${aihealthcare.perplexity.api-key:}") String apiKey) {
        this(promptLoaderService, apiKey, RestClient.builder().baseUrl(BASE_URL).build());
    }

    /**
     * Package-private constructor for unit testing — accepts a pre-built
     * {@link RestClient} so HTTP calls can be intercepted by a mock.
     *
     * @param promptLoaderService service for loading prompt templates
     * @param apiKey              Perplexity API key (may be blank to test guard path)
     * @param restClient          pre-configured RestClient (injected by tests)
     */
    PerplexityAiSearchAdapter(PromptLoaderService promptLoaderService,
                              String apiKey,
                              RestClient restClient) {
        log.debug("PerplexityAiSearchAdapter() | promptLoaderService={}, apiKeyPresent={}",
                  promptLoaderService.getClass().getSimpleName(), apiKey != null && !apiKey.isBlank());
        this.promptLoaderService = promptLoaderService;
        this.apiKey              = apiKey;
        this.restClient          = restClient;
        if (apiKey == null || apiKey.isBlank()) {
            log.info("PerplexityAiSearchAdapter() | PERPLEXITY_API_KEY not set — adapter will return fallback synthesis");
        } else {
            log.info("PerplexityAiSearchAdapter() | Perplexity Sonar API active for AI search (model={})", SONAR_MODEL);
        }
        log.debug("PerplexityAiSearchAdapter() | return=void");
    }

    @Override
    public AiSearchSynthesis synthesize(String query, List<NewsArticle> articles) {
        log.debug("synthesize() | query={}, articleCount={}", query, articles.size());

        if (apiKey == null || apiKey.isBlank()) {
            log.info("synthesize() | PERPLEXITY_API_KEY not configured — returning fallback");
            AiSearchSynthesis result = new AiSearchSynthesis(
                    MODEL_NAME, "Perplexity API key not configured.", new ArrayList<>(), Instant.now());
            log.debug("synthesize() | return={}", result);
            return result;
        }

        String prompt = buildPrompt(query, articles);
        log.info("synthesize() | sending prompt to Perplexity Sonar ({} chars)", prompt.length());

        String response = callPerplexityApi(prompt);
        log.info("synthesize() | received Perplexity response ({} chars)",
                 response == null ? 0 : response.length());

        AiSearchSynthesis result = parseResponse(response);
        log.debug("synthesize() | return={}", result);
        return result;
    }

    @Override
    public String modelName() {
        return MODEL_NAME;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String callPerplexityApi(String prompt) {
        log.debug("callPerplexityApi() | promptLength={}", prompt.length());

        Map<String, String> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(userMessage);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", SONAR_MODEL);
        requestBody.put("messages", messages);

        PerplexityApiResponse response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(PerplexityApiResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            log.warn("callPerplexityApi() | empty response from Perplexity");
            log.debug("callPerplexityApi() | return=null");
            return null;
        }

        String content = response.choices().get(0).message() != null
                ? response.choices().get(0).message().content()
                : null;

        log.debug("callPerplexityApi() | return=content[{} chars]", content == null ? 0 : content.length());
        return content;
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
            log.warn("parseResponse() | empty response from Perplexity");
            AiSearchSynthesis result = new AiSearchSynthesis(
                    MODEL_NAME, "No synthesis available.", new ArrayList<>(), Instant.now());
            log.debug("parseResponse() | return={}", result);
            return result;
        }

        if (response.trim().startsWith("NO_MATCH")) {
            log.info("parseResponse() | Perplexity reported NO_MATCH — articles not relevant to query");
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
            log.warn("parseResponse() | SUMMARY line not found in Perplexity response; using full response as summary");
            summary = response.length() > 4000 ? response.substring(0, 4000) + "..." : response;
        }

        AiSearchSynthesis result = new AiSearchSynthesis(MODEL_NAME, summary, keyFindings, Instant.now());
        log.debug("parseResponse() | return=AiSearchSynthesis[findings={}]", keyFindings.size());
        return result;
    }
}

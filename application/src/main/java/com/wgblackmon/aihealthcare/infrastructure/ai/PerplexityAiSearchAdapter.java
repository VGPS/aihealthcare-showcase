package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
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
 * <p>Delegates prompt building and response parsing to the shared
 * {@link AiSearchResponseParser} utility, which centralizes the
 * template loading and structured response extraction logic.
 *
 * <p>When {@code PERPLEXITY_API_KEY} is not set, the adapter returns a
 * graceful fallback synthesis rather than throwing an exception.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-02
 * @updated 2026-09-11
 */
@Slf4j
@Component
public class PerplexityAiSearchAdapter implements AiSearchPort {

    private static final String MODEL_NAME  = "Perplexity";
    private static final String BASE_URL    = "https://api.perplexity.ai";

    private final AiSearchResponseParser responseParser;
    private final String              apiKey;
    private final String              modelId;
    private final RestClient          restClient;

    /**
     * Spring-managed constructor — builds a {@link RestClient} targeting the
     * Perplexity base URL.  API key injected from
     * {@code aihealthcare.perplexity.api-key} (resolved from
     * {@code PERPLEXITY_API_KEY} env var; defaults to blank string when absent).
     *
     * @param responseParser shared parser for prompt building and response extraction
     * @param apiKey         Perplexity API key; blank when env var is not set
     * @param modelId        the configured Perplexity model ID
     */
    @Autowired
    public PerplexityAiSearchAdapter(
            AiSearchResponseParser responseParser,
            @Value("${aihealthcare.perplexity.api-key:}") String apiKey,
            @Value("${aihealthcare.perplexity.model:sonar}") String modelId) {
        this(responseParser, apiKey, modelId, RestClient.builder().baseUrl(BASE_URL).build());
    }

    /**
     * Package-private constructor for unit testing — accepts a pre-built
     * {@link RestClient} so HTTP calls can be intercepted by a mock.
     *
     * @param responseParser shared parser for prompt building and response extraction
     * @param apiKey         Perplexity API key (may be blank to test guard path)
     * @param modelId        the configured Perplexity model ID
     * @param restClient     pre-configured RestClient (injected by tests)
     */
    PerplexityAiSearchAdapter(AiSearchResponseParser responseParser,
                              String apiKey,
                              String modelId,
                              RestClient restClient) {
        log.debug("PerplexityAiSearchAdapter() | responseParser={}, apiKeyPresent={}, modelId={}",
                  responseParser.getClass().getSimpleName(), apiKey != null && !apiKey.isBlank(), modelId);
        this.responseParser      = responseParser;
        this.apiKey              = apiKey;
        this.modelId             = modelId;
        this.restClient          = restClient;
        if (apiKey == null || apiKey.isBlank()) {
            log.info("PerplexityAiSearchAdapter() | PERPLEXITY_API_KEY not set — adapter will return fallback synthesis");
        } else {
            log.info("PerplexityAiSearchAdapter() | Perplexity Sonar API active for AI search (model={})", modelId);
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

        String prompt = responseParser.buildPrompt(query, articles);
        log.info("synthesize() | sending prompt to Perplexity Sonar ({} chars)", prompt.length());

        String response = callPerplexityApi(prompt);
        log.info("synthesize() | received Perplexity response ({} chars)",
                 response == null ? 0 : response.length());

        AiSearchSynthesis result = responseParser.parseResponse(response, MODEL_NAME);
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

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("placeholder-set-");
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
        requestBody.put("model", modelId);
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

}

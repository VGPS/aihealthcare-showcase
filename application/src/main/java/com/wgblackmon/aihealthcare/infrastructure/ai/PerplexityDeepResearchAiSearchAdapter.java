package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity.PerplexityAgentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Perplexity Deep Research adapter for AI-enhanced search synthesis.
 *
 * <p>Implements {@link AiSearchPort} using the {@code sonar-deep-research} model
 * via {@code POST https://api.perplexity.ai/v1/agent} (Agent API). Unlike the
 * standard {@code PerplexityAiSearchAdapter} (which also uses the Agent API with
 * the {@code sonar} model for fast responses), this adapter invokes Perplexity's
 * Deep Research model which performs multiple internal web-search rounds before
 * synthesising — producing analyst-grade reports at the cost of 30–60 s latency.
 *
 * <p>A 90-second read timeout is configured via {@link SimpleClientHttpRequestFactory}
 * to accommodate the extended processing time. The model name surfaced to the UI
 * is {@value #MODEL_NAME}; it appears as a separate checkbox alongside the other
 * models on the AI Search page.
 *
 * <p>Note: the existing {@code PerplexityDeepResearchAdapter} (in the same package)
 * implements {@code TrendSummaryPort} using background Agent API polling — a separate
 * concern. This class runs synchronously (no background flag) for the AI Search flow.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-02
 * @updated 2026-10-04
 */
@Slf4j
@Component
public class PerplexityDeepResearchAiSearchAdapter implements AiSearchPort {

    static final String  MODEL_NAME    = "Perplexity Deep";
    private static final String  BASE_URL     = "https://api.perplexity.ai";
    private static final int     READ_TIMEOUT = 90_000;

    private final AiSearchResponseParser responseParser;
    private final String                 apiKey;
    private final String                 modelId;
    private final RestClient             restClient;

    /**
     * Spring-managed constructor — builds a {@link RestClient} with a 90-second
     * read timeout targeting the Perplexity base URL.
     *
     * @param responseParser shared parser for prompt building and response extraction
     * @param apiKey         Perplexity API key; blank when {@code PERPLEXITY_API_KEY} is unset
     * @param modelId        deep research model ID (default: {@code sonar-deep-research})
     */
    @Autowired
    public PerplexityDeepResearchAiSearchAdapter(
            AiSearchResponseParser responseParser,
            @Value("${aihealthcare.perplexity.api-key:}") String apiKey,
            @Value("${aihealthcare.perplexity.deep-research-model:sonar-deep-research}") String modelId) {
        this(responseParser, apiKey, modelId, buildRestClient());
    }

    /**
     * Package-private constructor for unit testing — accepts a pre-built
     * {@link RestClient} so HTTP calls can be intercepted by a mock.
     *
     * @param responseParser shared parser
     * @param apiKey         API key (may be blank to exercise the guard path)
     * @param modelId        model ID
     * @param restClient     pre-configured RestClient injected by tests
     */
    PerplexityDeepResearchAiSearchAdapter(AiSearchResponseParser responseParser,
                                          String apiKey,
                                          String modelId,
                                          RestClient restClient) {
        log.debug("PerplexityDeepResearchAiSearchAdapter() | responseParser={}, apiKeyPresent={}, modelId={}",
                  responseParser.getClass().getSimpleName(), apiKey != null && !apiKey.isBlank(), modelId);
        this.responseParser = responseParser;
        this.apiKey         = apiKey;
        this.modelId        = modelId;
        this.restClient     = restClient;
        if (apiKey == null || apiKey.isBlank()) {
            log.info("PerplexityDeepResearchAiSearchAdapter() | PERPLEXITY_API_KEY not set — adapter returns fallback");
        } else {
            log.info("PerplexityDeepResearchAiSearchAdapter() | Deep Research active (model={})", modelId);
        }
        log.debug("PerplexityDeepResearchAiSearchAdapter() | return=void");
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
        log.info("synthesize() | sending deep research prompt ({} chars) — expect 30–60 s", prompt.length());

        String response = callAgentApi(prompt);
        log.info("synthesize() | received response ({} chars)",
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

    private String callAgentApi(String prompt) {
        log.debug("callAgentApi() | promptLength={}", prompt.length());

        Map<String, String> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelId);
        requestBody.put("input", List.of(userMessage));

        PerplexityAgentResponse response = restClient.post()
                .uri("/v1/agent")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(PerplexityAgentResponse.class);

        if (response == null) {
            log.warn("callAgentApi() | null response from Perplexity Agent API");
            log.debug("callAgentApi() | return=null");
            return null;
        }

        if (response.isFailed()) {
            log.warn("callAgentApi() | Agent API returned failed/cancelled status");
            log.debug("callAgentApi() | return=null (failed status)");
            return null;
        }

        String content = response.extractText();
        log.debug("callAgentApi() | return=content[{} chars]", content == null ? 0 : content.length());
        return content;
    }

    private static RestClient buildRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(factory)
                .build();
    }
}

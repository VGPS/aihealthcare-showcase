package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.AiSearchSynthesis;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSearchPort;
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
 * ({@code POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent})
 * via {@link RestClient}.  Like the Perplexity adapter, Gemini does not have
 * a Spring AI starter — so this adapter calls the REST API directly.
 *
 * <p>Delegates prompt building and response parsing to the shared
 * {@link AiSearchResponseParser} utility, which centralizes the
 * template loading and structured response extraction logic.
 *
 * <p>When {@code GEMINI_API_KEY} is not set, the adapter returns a
 * graceful fallback synthesis rather than throwing an exception.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-09-11
 */
@Slf4j
@Component
public class GeminiAiSearchAdapter implements AiSearchPort {

    private static final String MODEL_NAME = "Gemini";
    private static final String BASE_URL   = "https://generativelanguage.googleapis.com";

    private final AiSearchResponseParser responseParser;
    private final String              apiKey;
    private final String              modelId;
    private final RestClient          restClient;

    /**
     * Spring-managed constructor — builds a {@link RestClient} targeting the
     * Google Generative AI base URL.  API key injected from
     * {@code aihealthcare.gemini.api-key} (resolved from
     * {@code GEMINI_API_KEY} env var; defaults to blank string when absent).
     *
     * @param responseParser shared parser for prompt building and response extraction
     * @param apiKey         Gemini API key; blank when env var is not set
     * @param modelId        the configured Gemini model ID
     */
    @Autowired
    public GeminiAiSearchAdapter(
            AiSearchResponseParser responseParser,
            @Value("${aihealthcare.gemini.api-key:}") String apiKey,
            @Value("${aihealthcare.gemini.model:gemini-3.5-flash}") String modelId) {
        this(responseParser, apiKey, modelId, RestClient.builder().baseUrl(BASE_URL).build());
    }

    /**
     * Package-private constructor for unit testing — accepts a pre-built
     * {@link RestClient} so HTTP calls can be intercepted by a mock.
     *
     * @param responseParser shared parser for prompt building and response extraction
     * @param apiKey         Gemini API key (may be blank to test guard path)
     * @param modelId        the configured Gemini model ID
     * @param restClient     pre-configured RestClient (injected by tests)
     */
    GeminiAiSearchAdapter(AiSearchResponseParser responseParser,
                          String apiKey,
                          String modelId,
                          RestClient restClient) {
        log.debug("GeminiAiSearchAdapter() | responseParser={}, apiKeyPresent={}, modelId={}",
                  responseParser.getClass().getSimpleName(), apiKey != null && !apiKey.isBlank(), modelId);
        this.responseParser      = responseParser;
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

        String prompt = responseParser.buildPrompt(query, articles);
        log.info("synthesize() | sending prompt to Gemini ({} chars)", prompt.length());

        String response = callGeminiApi(prompt);
        log.info("synthesize() | received Gemini response ({} chars)",
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

}

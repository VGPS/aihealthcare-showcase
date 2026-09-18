package com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig;
import com.wgblackmon.aihealthcare.domain.port.outbound.SearchPromptPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Infrastructure adapter that harvests AI-in-Healthcare articles via the
 * Perplexity Agent API ({@code POST https://api.perplexity.ai/v1/agent}).
 *
 * <p>When {@code PERPLEXITY_API_KEY} is set as an environment variable (resolved
 * via {@code aihealthcare.perplexity.api-key} in {@code application.yml}), this
 * adapter makes a live HTTP call to the Agent API with the {@code sonar} model
 * and the active PERPLEXITY search prompt.  The {@code web_search} tool is
 * forced via {@code tool_choice} so citations are always present.  Citation
 * URLs are extracted from the {@code search_results} output item (replacing the
 * old top-level {@code citations[]} array from the Sonar chat-completions API)
 * and mapped to {@link NewsArticle} records with {@code sourceTier="PERPLEXITY"}
 * and {@code sourceWeight=0.85}.
 *
 * <p>When the API key is absent or blank the adapter returns an empty list and
 * logs an info message — no exception is thrown.  The calling pipeline (e.g.
 * {@link com.wgblackmon.aihealthcare.infrastructure.research.PerplexityResearchAdapter})
 * handles empty results gracefully.
 *
 * <p>All API call failures are caught and logged; the method returns an empty list
 * so the research pipeline can always fall back to Google DB articles.
 *
 * <p>The harvest prompt is resolved at call time via {@link SearchPromptPort}
 * using the {@code "PERPLEXITY"} engine key.  If no active prompt exists the
 * adapter returns an empty list and logs a warning.
 *
 * <p>API endpoint:
 * <pre>
 * POST https://api.perplexity.ai/v1/agent
 * Authorization: Bearer {PERPLEXITY_API_KEY}
 * Content-Type: application/json
 * { "model": "perplexity/sonar", "input": [{"role": "user", "content": "{prompt}"}],
 *   "tools": [{"type": "web_search"}], "tool_choice": {"type": "web_search"} }
 * </pre>
 *
 * @author  Bill Blackmon
 * @version 3.0
 * @since   2026-04-28
 * @updated 2026-09-18
 */
@Slf4j
@Component
public class PerplexityHarvester {

    private static final String BASE_URL    = "https://api.perplexity.ai";

    private final SearchPromptPort searchPromptPort;
    private final String           apiKey;
    private final String           modelId;
    private final RestClient       restClient;

    /**
     * Spring-managed constructor — builds a {@link RestClient} targeting the
     * Perplexity base URL.  API key injected from
     * {@code aihealthcare.perplexity.api-key} (resolved from
     * {@code PERPLEXITY_API_KEY} env var; defaults to blank string when absent).
     *
     * @param searchPromptPort Port used to retrieve the active PERPLEXITY prompt.
     * @param apiKey           Perplexity API key; blank when env var is not set.
     */
    @Autowired
    public PerplexityHarvester(
            SearchPromptPort searchPromptPort,
            @Value("${aihealthcare.perplexity.api-key:}") String apiKey,
            @Value("${aihealthcare.perplexity.model:perplexity/sonar}") String modelId) {
        this(searchPromptPort, apiKey, modelId, RestClient.builder().baseUrl(BASE_URL).build());
    }

    /**
     * Package-private constructor for unit testing — accepts a pre-built
     * {@link RestClient} so HTTP calls can be intercepted by a mock.
     *
     * @param searchPromptPort Port used to retrieve the active PERPLEXITY prompt.
     * @param apiKey           Perplexity API key (may be blank to test guard path).
     * @param restClient       Pre-configured RestClient (injected by tests).
     */
    PerplexityHarvester(SearchPromptPort searchPromptPort, String apiKey, String modelId, RestClient restClient) {
        log.debug("PerplexityHarvester() | searchPromptPort={}, apiKeyPresent={}, modelId={}",
                  searchPromptPort.getClass().getSimpleName(), !apiKey.isBlank(), modelId);
        this.searchPromptPort = searchPromptPort;
        this.apiKey           = apiKey;
        this.modelId          = modelId;
        this.restClient       = restClient;
        if (apiKey.isBlank()) {
            log.info("PerplexityHarvester() | PERPLEXITY_API_KEY not set — harvester will return empty list");
        } else {
            log.info("PerplexityHarvester() | Perplexity Sonar API integration active (model={})", modelId);
        }
        log.debug("PerplexityHarvester() | return=void");
    }

    /**
     * Harvests AI-in-Healthcare articles for the given topic via the Perplexity
     * Sonar API.  Returns an empty list when the API key is absent, no active
     * PERPLEXITY prompt is configured, or the API call fails.
     *
     * @param topic The healthcare topic to research (e.g. "AI diagnostics").
     *              Uses {@code "AI in Healthcare"} when {@code null} or blank.
     * @return Harvested articles with {@code sourceTier="PERPLEXITY"}; never null.
     */
    public List<NewsArticle> harvestArticles(String topic) {
        log.debug("harvestArticles() | topic={}", topic);

        if (apiKey == null || apiKey.isBlank()) {
            log.info("harvestArticles() | PERPLEXITY_API_KEY not configured — returning empty");
            log.debug("harvestArticles() | return=[]");
            return Collections.emptyList();
        }

        Optional<SearchPromptConfig> promptOpt = searchPromptPort.findByEngine("PERPLEXITY");
        if (promptOpt.isEmpty() || !promptOpt.get().active()) {
            log.warn("harvestArticles() | No active PERPLEXITY search prompt — returning empty");
            log.debug("harvestArticles() | return=[]");
            return Collections.emptyList();
        }

        String resolvedTopic = (topic != null && !topic.isBlank()) ? topic : "AI in Healthcare";
        String prompt = promptOpt.get().templateText().replace("{topic}", resolvedTopic);
        log.info("harvestArticles() | calling Perplexity Sonar API for topic='{}'", resolvedTopic);

        try {
            List<NewsArticle> result = callPerplexityApi(resolvedTopic, prompt);
            log.debug("harvestArticles() | return={} articles", result.size());
            return result;
        } catch (Exception ex) {
            log.error("harvestArticles() | Perplexity API call failed: {}", ex.getMessage());
            log.debug("harvestArticles() | return=[] (error fallback)");
            return Collections.emptyList();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private List<NewsArticle> callPerplexityApi(String topic, String prompt) {
        log.debug("callPerplexityApi() | topic={}", topic);

        // Build request body as a plain Map so Jackson serializes it correctly
        Map<String, String> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(userMessage);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelId);
        requestBody.put("input", messages);
        requestBody.put("tools", List.of(Map.of("type", "web_search")));
        requestBody.put("tool_choice", Map.of("type", "web_search"));

        PerplexityAgentResponse response = restClient.post()
                .uri("/v1/agent")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(PerplexityAgentResponse.class);

        if (response == null || !response.isCompleted()) {
            log.info("callPerplexityApi() | empty or failed response from Perplexity");
            log.debug("callPerplexityApi() | return=[]");
            return Collections.emptyList();
        }

        List<String> citationUrls = response.extractCitationUrls();
        if (citationUrls.isEmpty()) {
            log.info("callPerplexityApi() | no citations in Perplexity response");
            log.debug("callPerplexityApi() | return=[]");
            return Collections.emptyList();
        }

        String content = response.extractText();
        if (content == null) content = "";

        log.info("callPerplexityApi() | received {} citations, content length={}",
                 citationUrls.size(), content.length());

        List<NewsArticle> result = mapCitationsToArticles(citationUrls, content, topic);
        log.debug("callPerplexityApi() | return={} articles", result.size());
        return result;
    }

    private List<NewsArticle> mapCitationsToArticles(
            List<String> citations, String content, String topic) {
        log.debug("mapCitationsToArticles() | citations={}, topic={}", citations.size(), topic);

        List<NewsArticle> result = new ArrayList<>();
        for (int i = 0; i < citations.size(); i++) {
            String url = citations.get(i);
            int citationNumber = i + 1;
            String snippet = extractSnippet(content, citationNumber);
            String title   = buildTitle(url, citationNumber);

            try {
                String urlHash = Integer.toHexString(url.hashCode());
                result.add(new NewsArticle(
                        "perplexity-" + topicSlug(topic) + "-" + urlHash,
                        title,
                        URI.create(url),
                        snippet,
                        topic,
                        null,
                        1L,
                        "Perplexity Sonar",
                        "PERPLEXITY",
                        0.85,
                        Instant.now()));
            } catch (Exception e) {
                log.warn("mapCitationsToArticles() | skipping invalid citation[{}]: {} — {}",
                         citationNumber, url, e.getMessage());
            }
        }

        log.debug("mapCitationsToArticles() | return={} articles", result.size());
        return result;
    }

    /**
     * Extracts the sentence(s) surrounding citation marker {@code [{n}]} from
     * the Perplexity response content.  Returns a 400-char window centred on the
     * marker, or the first 300 chars of content when the marker is not found.
     */
    private String extractSnippet(String content, int citationNumber) {
        log.debug("extractSnippet() | citationNumber={}", citationNumber);

        if (content == null || content.isBlank()) {
            log.debug("extractSnippet() | return=(empty)");
            return "";
        }

        String marker = "[" + citationNumber + "]";
        int markerIndex = content.indexOf(marker);

        if (markerIndex < 0) {
            String fallback = content.length() > 300 ? content.substring(0, 300) + "\u2026" : content;
            log.debug("extractSnippet() | marker not found — using content prefix");
            log.debug("extractSnippet() | return=(fallback len={})", fallback.length());
            return fallback;
        }

        int start   = Math.max(0, markerIndex - 200);
        int end     = Math.min(content.length(), markerIndex + marker.length() + 200);
        String snippet = content.substring(start, end).trim();

        log.debug("extractSnippet() | return=(snippet len={})", snippet.length());
        return snippet;
    }

    /**
     * Builds a human-readable title from a citation URL by combining the
     * host name with the first meaningful path segment. Falls back to the
     * bare host name (no bracketed citation number \u2014 the title becomes the
     * visible anchor text of an already-clickable link, so a redundant
     * "[n]" marker next to it is just noise) when no usable path segment
     * exists, or to {@code "Source"} on any parsing error.
     */
    private String buildTitle(String url, int citationNumber) {
        log.debug("buildTitle() | url={}, citationNumber={}", url, citationNumber);
        try {
            URI uri  = URI.create(url);
            String host = uri.getHost() != null ? uri.getHost().replace("www.", "") : url;
            String path = uri.getPath();

            if (path != null && path.length() > 1) {
                String[] segments = path.split("/");
                for (String segment : segments) {
                    if (segment != null && !segment.isBlank() && segment.length() > 3) {
                        String slug  = segment.replace("-", " ").replace("_", " ");
                        String title = host + " \u2014 " + slug;
                        log.debug("buildTitle() | return={}", title);
                        return title;
                    }
                }
            }

            log.debug("buildTitle() | return={}", host);
            return host;
        } catch (Exception e) {
            log.debug("buildTitle() | return=Source (fallback)");
            return "Source";
        }
    }

    private String topicSlug(String topic) {
        if (topic == null || topic.isBlank()) return "unknown";
        return topic.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}

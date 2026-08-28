package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.TrendSummaryPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adapter that implements {@link TrendSummaryPort} using the Perplexity Sonar
 * Deep Research async API ({@code POST /v1/async/sonar}, {@code GET /v1/async/sonar/{id}}).
 *
 * <p>Deep Research performs multi-step web research with citations, producing
 * comprehensive analyst-grade reports. The API is asynchronous: a job is
 * submitted and then polled until completion or timeout.
 *
 * <p>The {@code message.content} field in the response often contains a leading
 * {@code <think>...</think>} reasoning block that must be stripped before the
 * content is ready for display.
 *
 * <p>When {@code PERPLEXITY_API_KEY} is absent, the adapter returns {@code null}
 * gracefully (no exception).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-28
 * @updated 2026-08-28
 */
@Slf4j
@Component
public class PerplexityDeepResearchAdapter implements TrendSummaryPort {

    private static final String BASE_URL = "https://api.perplexity.ai";
    private static final String MODEL_ID = "sonar-deep-research";
    private static final Pattern THINK_BLOCK = Pattern.compile(
            "<think>.*?</think>\\s*", Pattern.DOTALL);

    private final String apiKey;
    private final RestClient restClient;
    private final long pollIntervalMs;
    private final long pollTimeoutMs;

    /**
     * Spring-managed constructor — builds a {@link RestClient} targeting the
     * Perplexity base URL. API key resolved from {@code PERPLEXITY_API_KEY}
     * env var via {@code aihealthcare.perplexity.api-key}.
     *
     * @param apiKey          Perplexity API key; blank when env var is not set
     * @param pollIntervalMs  interval between poll requests in ms (default 5000)
     * @param pollTimeoutMs   max time to wait for job completion in ms (default 300000 = 5 min)
     */
    @Autowired
    public PerplexityDeepResearchAdapter(
            @Value("${aihealthcare.perplexity.api-key:}") String apiKey,
            @Value("${aihealthcare.deep-research.poll-interval-ms:5000}") long pollIntervalMs,
            @Value("${aihealthcare.deep-research.poll-timeout-ms:300000}") long pollTimeoutMs) {
        this(apiKey, pollIntervalMs, pollTimeoutMs, RestClient.builder().baseUrl(BASE_URL).build());
    }

    /**
     * Package-private constructor for unit testing — accepts a pre-built
     * {@link RestClient} so HTTP calls can be intercepted by WireMock.
     *
     * @param apiKey          Perplexity API key (may be blank to test guard path)
     * @param pollIntervalMs  interval between poll requests in ms
     * @param pollTimeoutMs   max time to wait for completion in ms
     * @param restClient      pre-configured RestClient
     */
    PerplexityDeepResearchAdapter(String apiKey, long pollIntervalMs,
                                   long pollTimeoutMs, RestClient restClient) {
        log.debug("PerplexityDeepResearchAdapter() | apiKeyPresent={}, pollIntervalMs={}, pollTimeoutMs={}",
                  apiKey != null && !apiKey.isBlank(), pollIntervalMs, pollTimeoutMs);
        this.apiKey = apiKey;
        this.pollIntervalMs = pollIntervalMs;
        this.pollTimeoutMs = pollTimeoutMs;
        this.restClient = restClient;
        if (apiKey == null || apiKey.isBlank()) {
            log.info("PerplexityDeepResearchAdapter() | PERPLEXITY_API_KEY not set — deep research disabled");
        } else {
            log.info("PerplexityDeepResearchAdapter() | Deep Research adapter active (model={})", MODEL_ID);
        }
        log.debug("PerplexityDeepResearchAdapter() | return=void");
    }

    @Override
    public String generateSummary(String keyword, List<NewsArticle> articles) {
        log.debug("generateSummary() | keyword={}, articleCount={}",
                  keyword, articles != null ? articles.size() : 0);

        if (!isAvailable()) {
            log.debug("generateSummary() | return=null (not available)");
            return null;
        }

        String prompt = buildPrompt(keyword, articles);

        // Step 1: Submit async job
        DeepResearchResponse submitResponse = submitJob(prompt);
        if (submitResponse == null || submitResponse.id() == null) {
            log.warn("generateSummary() | submit failed for keyword={}", keyword);
            log.debug("generateSummary() | return=null (submit failed)");
            return null;
        }

        String jobId = submitResponse.id();
        log.info("generateSummary() | job submitted: id={}, keyword={}", jobId, keyword);

        // Step 2: Poll until terminal state or timeout
        DeepResearchResponse finalResponse = pollUntilDone(jobId);
        if (finalResponse == null || !finalResponse.isCompleted()) {
            String status = finalResponse != null ? finalResponse.status() : "null";
            log.warn("generateSummary() | poll ended without completion: keyword={}, status={}",
                     keyword, status);
            log.debug("generateSummary() | return=null (not completed)");
            return null;
        }

        // Step 3: Extract and clean content
        String rawContent = finalResponse.extractContent();
        if (rawContent == null || rawContent.isBlank()) {
            log.warn("generateSummary() | completed but empty content for keyword={}", keyword);
            log.debug("generateSummary() | return=null (empty content)");
            return null;
        }

        String cleaned = stripThinkBlocks(rawContent);

        if (finalResponse.usage() != null && finalResponse.usage().cost() != null) {
            DeepResearchResponse.UsageCost cost = finalResponse.usage().cost();
            double totalCost = cost.inputTokensCost() + cost.outputTokensCost()
                    + cost.citationTokensCost() + cost.reasoningTokensCost()
                    + cost.searchQueriesCost();
            log.info("generateSummary() | keyword={}, cost=${}, tokens={}",
                     keyword, String.format("%.4f", totalCost), finalResponse.usage().totalTokens());
        }

        log.debug("generateSummary() | return={} chars", cleaned.length());
        return cleaned;
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("placeholder-set-");
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the deep research prompt from keyword and article context.
     */
    String buildPrompt(String keyword, List<NewsArticle> articles) {
        log.debug("buildPrompt() | keyword={}, articleCount={}",
                  keyword, articles != null ? articles.size() : 0);

        StringBuilder sb = new StringBuilder();
        sb.append("You are a healthcare AI industry analyst writing for investors and executives.\n\n");
        sb.append("Research the current state of \"").append(keyword).append("\" ");
        sb.append("in healthcare AI. Produce a 3-5 paragraph executive brief covering:\n");
        sb.append("1. What is driving this trend right now (specific products, studies, regulatory actions)\n");
        sb.append("2. Key companies and organizations involved\n");
        sb.append("3. Market implications and investment relevance\n");
        sb.append("4. What to watch for in the next 6-12 months\n\n");

        if (articles != null && !articles.isEmpty()) {
            sb.append("For additional context, here are recent articles on this topic:\n");
            int limit = Math.min(articles.size(), 10);
            for (int i = 0; i < limit; i++) {
                NewsArticle article = articles.get(i);
                sb.append("- ").append(article.title());
                if (article.sourceName() != null) {
                    sb.append(" (").append(article.sourceName()).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        sb.append("Write in flowing prose suitable for an executive brief. ");
        sb.append("Be specific and cite concrete developments. ");
        sb.append("Do not use bullet points or numbered lists in the output.");

        String result = sb.toString();
        log.debug("buildPrompt() | return=prompt ({} chars)", result.length());
        return result;
    }

    /**
     * Submits the deep research job to {@code POST /v1/async/sonar}.
     */
    DeepResearchResponse submitJob(String prompt) {
        log.debug("submitJob() | promptLength={}", prompt.length());

        Map<String, String> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(userMessage);

        Map<String, Object> innerRequest = new LinkedHashMap<>();
        innerRequest.put("model", MODEL_ID);
        innerRequest.put("messages", messages);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("request", innerRequest);

        try {
            DeepResearchResponse response = restClient.post()
                    .uri("/v1/async/sonar")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(DeepResearchResponse.class);

            log.debug("submitJob() | return=id={}, status={}",
                      response != null ? response.id() : "null",
                      response != null ? response.status() : "null");
            return response;
        } catch (Exception e) {
            log.warn("submitJob() | API call failed: {}", e.getMessage());
            log.debug("submitJob() | return=null (error)");
            return null;
        }
    }

    /**
     * Polls {@code GET /v1/async/sonar/{id}} until the job reaches a terminal
     * state or the configured timeout is exceeded.
     */
    DeepResearchResponse pollUntilDone(String jobId) {
        log.debug("pollUntilDone() | jobId={}, timeoutMs={}", jobId, pollTimeoutMs);

        long deadline = System.currentTimeMillis() + pollTimeoutMs;
        DeepResearchResponse response = null;

        while (System.currentTimeMillis() < deadline) {
            try {
                response = restClient.get()
                        .uri("/v1/async/sonar/{id}", jobId)
                        .header("Authorization", "Bearer " + apiKey)
                        .retrieve()
                        .body(DeepResearchResponse.class);

                if (response != null && response.isTerminal()) {
                    log.info("pollUntilDone() | job {} reached terminal status: {}",
                             jobId, response.status());
                    log.debug("pollUntilDone() | return={}", response.status());
                    return response;
                }

                String currentStatus = response != null ? response.status() : "null";
                log.debug("pollUntilDone() | job {} status={}, waiting {}ms",
                          jobId, currentStatus, pollIntervalMs);

            } catch (Exception e) {
                log.warn("pollUntilDone() | poll error for job {}: {}", jobId, e.getMessage());
            }

            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("pollUntilDone() | interrupted while polling job {}", jobId);
                log.debug("pollUntilDone() | return=null (interrupted)");
                return null;
            }
        }

        log.warn("pollUntilDone() | timeout waiting for job {}", jobId);
        log.debug("pollUntilDone() | return={} (timeout)", response != null ? response.status() : "null");
        return response;
    }

    /**
     * Strips {@code <think>...</think>} reasoning blocks from the content.
     * Perplexity Deep Research often prepends a reasoning block before the
     * actual report text.
     *
     * @param content raw content from the API response
     * @return content with think blocks removed and leading whitespace trimmed
     */
    static String stripThinkBlocks(String content) {
        if (content == null) {
            return null;
        }
        Matcher matcher = THINK_BLOCK.matcher(content);
        return matcher.replaceAll("").trim();
    }
}

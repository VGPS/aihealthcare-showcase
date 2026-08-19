package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.perplexity;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.MarketNewsResearchPort;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity.PerplexityApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Perplexity Sonar adapter implementing {@link MarketNewsResearchPort}.
 *
 * <p>Issues a structured prompt to the Perplexity Sonar web-search API and
 * parses ITEM/END_ITEM blocks from the response into {@link MarketNewsItem}
 * records. The prompt includes the {@code since} timestamp so Perplexity
 * focuses on events published after that date.
 *
 * <p>When {@code PERPLEXITY_API_KEY} is absent the adapter returns an empty
 * list rather than throwing — callers must handle that gracefully (the
 * scheduler will log a warning and skip the digest for that run).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class PerplexityMarketNewsAdapter implements MarketNewsResearchPort {

    private static final String BASE_URL       = "https://api.perplexity.ai";
    private static final String PROMPT_FILE    = "market-news-research.txt";

    private final PromptLoaderService promptLoader;
    private final String              apiKey;
    private final String              modelId;
    private final RestClient          restClient;

    public PerplexityMarketNewsAdapter(
            PromptLoaderService promptLoader,
            @Value("${aihealthcare.perplexity.api-key:}") String apiKey,
            @Value("${aihealthcare.perplexity.market-news.model:sonar}") String modelId) {
        this(promptLoader, apiKey, modelId, RestClient.builder().baseUrl(BASE_URL).build());
    }

    /** Package-private for testing — accepts a pre-built RestClient. */
    PerplexityMarketNewsAdapter(PromptLoaderService promptLoader,
                                String apiKey,
                                String modelId,
                                RestClient restClient) {
        log.debug("PerplexityMarketNewsAdapter() | apiKeyPresent={}, modelId={}",
                apiKey != null && !apiKey.isBlank(), modelId);
        this.promptLoader = promptLoader;
        this.apiKey       = apiKey;
        this.modelId      = modelId;
        this.restClient   = restClient;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("PerplexityMarketNewsAdapter() | PERPLEXITY_API_KEY not set — market news research disabled");
        }
        log.debug("PerplexityMarketNewsAdapter() | return=void");
    }

    @Override
    public List<MarketNewsItem> findRecentAiHealthcareNews(Instant since) {
        log.debug("findRecentAiHealthcareNews() | since={}", since);

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("findRecentAiHealthcareNews() | API key absent — returning empty list");
            log.debug("findRecentAiHealthcareNews() | return=[]");
            return new ArrayList<>();
        }

        String sinceDate = LocalDate.ofInstant(since, ZoneOffset.UTC).toString();
        String prompt = promptLoader.load(PROMPT_FILE).replace("{since_date}", sinceDate);

        log.info("findRecentAiHealthcareNews() | querying Perplexity for market news since {}", sinceDate);
        String rawResponse = callApi(prompt);
        log.info("findRecentAiHealthcareNews() | received response ({} chars)",
                rawResponse == null ? 0 : rawResponse.length());

        List<MarketNewsItem> result = parseItems(rawResponse, since);
        log.info("findRecentAiHealthcareNews() | parsed {} market news items", result.size());
        log.debug("findRecentAiHealthcareNews() | return={}", result);
        return result;
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private String callApi(String prompt) {
        log.debug("callApi() | promptLength={}", prompt.length());

        Map<String, String> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(userMessage);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelId);
        body.put("messages", messages);

        PerplexityApiResponse response = restClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(PerplexityApiResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            log.warn("callApi() | empty response from Perplexity");
            log.debug("callApi() | return=null");
            return null;
        }

        String content = response.choices().get(0).message() != null
                ? response.choices().get(0).message().content()
                : null;

        log.debug("callApi() | return=content[{} chars]", content == null ? 0 : content.length());
        return content;
    }

    private List<MarketNewsItem> parseItems(String raw, Instant fallbackPublishedAt) {
        log.debug("parseItems() | rawLength={}", raw == null ? 0 : raw.length());

        List<MarketNewsItem> results = new ArrayList<>();

        if (raw == null || raw.isBlank() || raw.contains("NO_RESULTS")) {
            log.debug("parseItems() | return=[]");
            return results;
        }

        String headline      = null;
        String summary       = null;
        String category      = null;
        String dealSizeRaw   = null;
        String publishedAtRaw = null;
        List<String> sources = new ArrayList<>();
        boolean inItem       = false;

        for (String line : raw.split("\n")) {
            String trimmed = line.trim();

            if (trimmed.equals("ITEM")) {
                inItem        = true;
                headline      = null;
                summary       = null;
                category      = null;
                dealSizeRaw   = null;
                publishedAtRaw = null;
                sources       = new ArrayList<>();

            } else if (trimmed.equals("END_ITEM")) {
                if (inItem && headline != null && summary != null && category != null) {
                    MarketNewsItem item = buildItem(
                            headline, summary, category, dealSizeRaw,
                            publishedAtRaw, sources, fallbackPublishedAt);
                    if (item != null) {
                        results.add(item);
                    }
                } else {
                    log.warn("parseItems() | incomplete ITEM block skipped — headline={}, summary={}, category={}",
                            headline, summary != null, category);
                }
                inItem = false;

            } else if (inItem) {
                if (trimmed.startsWith("HEADLINE: ")) {
                    headline = trimmed.substring("HEADLINE: ".length()).trim();
                } else if (trimmed.startsWith("SUMMARY: ")) {
                    summary = trimmed.substring("SUMMARY: ".length()).trim();
                } else if (trimmed.startsWith("CATEGORY: ")) {
                    category = trimmed.substring("CATEGORY: ".length()).trim();
                } else if (trimmed.startsWith("DEAL_SIZE_USD: ")) {
                    dealSizeRaw = trimmed.substring("DEAL_SIZE_USD: ".length()).trim();
                } else if (trimmed.startsWith("PUBLISHED_AT: ")) {
                    publishedAtRaw = trimmed.substring("PUBLISHED_AT: ".length()).trim();
                } else if (trimmed.startsWith("SOURCES: ")) {
                    String sourcesRaw = trimmed.substring("SOURCES: ".length()).trim();
                    for (String s : sourcesRaw.split("\\|")) {
                        String url = s.trim();
                        if (!url.isBlank()) {
                            sources.add(url);
                        }
                    }
                }
            }
        }

        log.debug("parseItems() | return={} items", results.size());
        return results;
    }

    private MarketNewsItem buildItem(String headline, String summary, String categoryStr,
                                     String dealSizeRaw, String publishedAtRaw,
                                     List<String> sources, Instant fallbackPublishedAt) {
        NewsCategory category;
        try {
            category = NewsCategory.valueOf(categoryStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("buildItem() | unknown category '{}' — defaulting to OTHER", categoryStr);
            category = NewsCategory.OTHER;
        }

        Long dealSizeUsd = null;
        if (dealSizeRaw != null && !dealSizeRaw.isBlank() && !"NONE".equalsIgnoreCase(dealSizeRaw)) {
            try {
                String digits = dealSizeRaw.replaceAll("[^0-9]", "");
                if (!digits.isBlank()) {
                    dealSizeUsd = Long.parseLong(digits);
                }
            } catch (NumberFormatException e) {
                log.warn("buildItem() | unparseable DEAL_SIZE_USD '{}' — leaving null", dealSizeRaw);
            }
        }

        Instant publishedAt = fallbackPublishedAt;
        if (publishedAtRaw != null && !publishedAtRaw.isBlank() && !"UNKNOWN".equalsIgnoreCase(publishedAtRaw)) {
            try {
                publishedAt = Instant.parse(publishedAtRaw);
            } catch (DateTimeParseException e) {
                log.warn("buildItem() | unparseable PUBLISHED_AT '{}' — using fallback", publishedAtRaw);
            }
        }

        try {
            return new MarketNewsItem(headline, summary, sources, publishedAt, category, dealSizeUsd);
        } catch (IllegalArgumentException e) {
            log.warn("buildItem() | invalid MarketNewsItem — skipping: {}", e.getMessage());
            return null;
        }
    }
}

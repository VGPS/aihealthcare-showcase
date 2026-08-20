package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.alpaca;

import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.SecondaryNewsCheckPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Alpaca News API adapter implementing {@link SecondaryNewsCheckPort}.
 *
 * <p>Uses the Alpaca Markets News endpoint
 * ({@code GET https://data.alpaca.markets/v1beta1/news}) to retrieve
 * Benzinga-sourced news articles tagged with the configured tracked-ticker
 * symbols. This provides a secondary cross-check over same-day headlines
 * that the primary Perplexity Search adapter may have missed.
 *
 * <p>The Alpaca News API is free with the same Alpaca account used by
 * {@link AlpacaMarketDataAdapter} — no additional vendor or cost.
 *
 * <p>This adapter is a secondary, non-replacement source:
 * <ul>
 *   <li>Only covers the configured {@code tracked-tickers} list (typically
 *       public companies with Alpaca-tagged symbols).</li>
 *   <li>Does NOT replace the Perplexity adapter, which covers private companies,
 *       regulatory filings, and general web coverage.</li>
 *   <li>Returns an empty list (never throws) on API failure.</li>
 * </ul>
 *
 * <p>Category is inferred from simple headline keywords before the item enters
 * the shared pipeline; the {@code ClaudeImpactClassifierAdapter} performs the
 * authoritative classification in the next pipeline step.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@Component
public class AlpacaNewsAdapter implements SecondaryNewsCheckPort {

    private static final String BASE_URL = "https://data.alpaca.markets";
    private static final int    MAX_LIMIT = 50;

    private final String       apiKey;
    private final String       secretKey;
    private final List<String> trackedTickers;
    private final RestClient   restClient;

    @Autowired
    public AlpacaNewsAdapter(
            @Value("${aihealthcare.alpaca.api-key:}") String apiKey,
            @Value("${aihealthcare.alpaca.secret-key:}") String secretKey,
            @Value("${aihealthcare.alpaca.tracked-tickers:DOCS,AMWL,EVH,NVCR,ASTH}") String tickersStr) {
        this(apiKey, secretKey, tickersStr, RestClient.builder().baseUrl(BASE_URL).build());
    }

    /** Package-private for testing — accepts a pre-built RestClient. */
    AlpacaNewsAdapter(String apiKey, String secretKey, String tickersStr, RestClient restClient) {
        log.debug("AlpacaNewsAdapter() | apiKeyPresent={}, secretKeyPresent={}, tickersStr={}",
                apiKey != null && !apiKey.isBlank(), secretKey != null && !secretKey.isBlank(), tickersStr);
        this.apiKey  = apiKey;
        this.secretKey = secretKey;
        this.restClient = restClient;
        List<String> parsed = new ArrayList<>();
        if (tickersStr != null && !tickersStr.isBlank()) {
            for (String t : tickersStr.split(",")) {
                String trimmed = t.trim();
                if (!trimmed.isEmpty()) {
                    parsed.add(trimmed.toUpperCase());
                }
            }
        }
        this.trackedTickers = parsed;
        if (!keysPresent()) {
            log.warn("AlpacaNewsAdapter() | ALPACA_API_KEY_ID or ALPACA_API_SECRET_KEY not set — secondary news check disabled");
        }
        log.debug("AlpacaNewsAdapter() | return=void (trackedTickers={})", trackedTickers);
    }

    @Override
    public List<MarketNewsItem> findRecentNews(Instant since, Instant until) {
        log.debug("findRecentNews() | since={}, until={}", since, until);

        if (!keysPresent() || trackedTickers.isEmpty()) {
            log.debug("findRecentNews() | return=empty (no credentials or no tracked tickers)");
            return List.of();
        }

        String symbols = String.join(",", trackedTickers);

        try {
            AlpacaNewsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1beta1/news")
                            .queryParam("symbols", symbols)
                            .queryParam("start", since.toString())
                            .queryParam("end", until.toString())
                            .queryParam("limit", MAX_LIMIT)
                            .queryParam("sort", "desc")
                            .build())
                    .header("APCA-API-KEY-ID", apiKey)
                    .header("APCA-API-SECRET-KEY", secretKey)
                    .retrieve()
                    .body(AlpacaNewsResponse.class);

            if (response == null || response.news() == null || response.news().isEmpty()) {
                log.info("findRecentNews() | Alpaca returned no articles for tickers={}", symbols);
                log.debug("findRecentNews() | return=empty");
                return List.of();
            }

            List<MarketNewsItem> result = new ArrayList<>();
            for (AlpacaNewsResponse.AlpacaNewsArticle article : response.news()) {
                MarketNewsItem item = toMarketNewsItem(article);
                if (item != null) {
                    result.add(item);
                }
            }

            log.info("findRecentNews() | mapped {} articles from Alpaca for tickers={}", result.size(), symbols);
            log.debug("findRecentNews() | return.size={}", result.size());
            return result;

        } catch (HttpClientErrorException e) {
            log.warn("findRecentNews() | HTTP {} from Alpaca News API: {}", e.getStatusCode(), e.getMessage());
            log.debug("findRecentNews() | return=empty");
            return List.of();
        } catch (Exception e) {
            log.warn("findRecentNews() | Alpaca News API error (non-fatal): {}", e.getMessage());
            log.debug("findRecentNews() | return=empty");
            return List.of();
        }
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    private boolean keysPresent() {
        return apiKey != null && !apiKey.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }

    /**
     * Maps an Alpaca news article to a {@link MarketNewsItem}.
     *
     * <p>Returns {@code null} if the article has no headline or URL (the item
     * would fail {@link MarketNewsItem}'s non-blank validation anyway).
     * Category is inferred by lightweight keyword matching on the headline;
     * the Claude impact classifier performs the authoritative pass later.
     */
    MarketNewsItem toMarketNewsItem(AlpacaNewsResponse.AlpacaNewsArticle article) {
        if (article.headline() == null || article.headline().isBlank()) {
            log.debug("toMarketNewsItem() | skipping article id={} — blank headline", article.id());
            return null;
        }
        if (article.url() == null || article.url().isBlank()) {
            log.debug("toMarketNewsItem() | skipping article id={} — no URL", article.id());
            return null;
        }

        String summary = (article.summary() != null && !article.summary().isBlank())
                ? article.summary()
                : article.headline();

        Instant publishedAt = article.updatedAt() != null ? article.updatedAt() : Instant.now();
        NewsCategory category = inferCategory(article.headline());

        return new MarketNewsItem(
                article.headline(),
                summary,
                List.of(article.url()),
                publishedAt,
                category,
                null
        );
    }

    /**
     * Infers a {@link NewsCategory} from headline keywords.
     *
     * <p>This is a best-effort pre-classification only. The Claude impact
     * classifier will override this with an authoritative category in the
     * next pipeline step.
     */
    NewsCategory inferCategory(String headline) {
        String lower = headline.toLowerCase();
        if (lower.contains("acqui") || lower.contains("merger") || lower.contains("buyout")) {
            return NewsCategory.M_AND_A;
        }
        if (lower.contains("earns") || lower.contains("earnings") || lower.contains("revenue")
                || lower.contains("q1 ") || lower.contains("q2 ") || lower.contains("q3 ") || lower.contains("q4 ")) {
            return NewsCategory.EARNINGS;
        }
        if (lower.contains("fda") || lower.contains("clearance") || lower.contains("approval")
                || lower.contains("cms") || lower.contains("regulation")) {
            return NewsCategory.REGULATORY;
        }
        if (lower.contains("partner") || lower.contains("collaboration") || lower.contains("deal")) {
            return NewsCategory.MAJOR_PARTNERSHIP;
        }
        if (lower.contains("funding") || lower.contains("raise") || lower.contains("series ")) {
            return NewsCategory.FUNDING;
        }
        return NewsCategory.OTHER;
    }

    /** Returns the list of tracked tickers this adapter was configured with. */
    List<String> getTrackedTickers() {
        return trackedTickers;
    }
}

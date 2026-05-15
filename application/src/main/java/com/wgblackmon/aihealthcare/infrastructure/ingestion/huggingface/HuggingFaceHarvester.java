package com.wgblackmon.aihealthcare.infrastructure.ingestion.huggingface;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceConfig;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceConfig.FeedTier;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Infrastructure adapter that queries the HuggingFace Models API to discover
 * healthcare-related LLMs and maps them to {@link NewsArticle} records.
 *
 * <p>Uses the public HuggingFace API endpoint configured as a
 * {@code HUGGINGFACE}-tier source in {@link FeedSourceProperties}.  No
 * authentication is required for read-only model search.
 *
 * <p>Each discovered model is mapped to a {@link NewsArticle} with:
 * <ul>
 *   <li>{@code articleId} — {@code "hf-" + modelId} for stable dedup</li>
 *   <li>{@code url} — the model's HuggingFace page</li>
 *   <li>{@code bodyText} — pipeline tag, download count, and tags</li>
 *   <li>{@code sourceTier} — {@code "HUGGINGFACE"}</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
@Slf4j
@Component
public class HuggingFaceHarvester {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_BODY_LENGTH = 10_000;

    private final List<FeedSourceConfig> huggingFaceSources;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public HuggingFaceHarvester(FeedSourceProperties properties) {
        log.debug("HuggingFaceHarvester() | properties={}",
                  properties.getClass().getSimpleName());
        List<FeedSourceConfig> filtered = new ArrayList<>();
        for (FeedSourceConfig config : properties.toFeedSourceConfigs()) {
            if (config.tier() == FeedTier.HUGGINGFACE) {
                filtered.add(config);
            }
        }
        this.huggingFaceSources = List.copyOf(filtered);
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        log.debug("HuggingFaceHarvester() | initialized with {} HuggingFace sources",
                  huggingFaceSources.size());
    }

    /**
     * Package-private constructor for testing with injected dependencies.
     */
    HuggingFaceHarvester(List<FeedSourceConfig> sources,
                         ObjectMapper objectMapper,
                         HttpClient httpClient) {
        this.huggingFaceSources = List.copyOf(sources);
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    /**
     * Queries the HuggingFace API and returns healthcare-related models as
     * {@link NewsArticle} records.
     *
     * @return list of articles representing discovered models (never null)
     */
    public List<NewsArticle> harvestModels() {
        log.debug("harvestModels() | checking {} HuggingFace sources",
                  huggingFaceSources.size());
        List<NewsArticle> results = new ArrayList<>();

        for (FeedSourceConfig source : huggingFaceSources) {
            try {
                List<NewsArticle> models = harvestFromSource(source);
                results.addAll(models);
            } catch (Exception ex) {
                log.error("harvestModels() | failed to harvest '{}': {}",
                          source.name(), ex.getMessage(), ex);
            }
        }

        log.info("harvestModels() | discovered {} HuggingFace models", results.size());
        log.debug("harvestModels() | return={} articles", results.size());
        return List.copyOf(results);
    }

    /**
     * Queries a single HuggingFace API endpoint and maps the response.
     */
    private List<NewsArticle> harvestFromSource(FeedSourceConfig source) throws Exception {
        log.debug("harvestFromSource() | source={}, url={}", source.name(), source.url());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(source.url()))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        Instant startTime = Instant.now();
        log.debug("harvestFromSource() | PRE-FETCH  url={} startTime={}", source.url(), startTime);

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        Instant endTime = Instant.now();
        long elapsedMs = java.time.Duration.between(startTime, endTime).toMillis();
        String preview = response.body().length() > 500
                ? response.body().substring(0, 500) + "..." : response.body();
        log.debug("harvestFromSource() | POST-FETCH url={} endTime={} elapsedMs={} status={} contentLength={} contentPreview={}",
                  source.url(), endTime, elapsedMs, response.statusCode(),
                  response.body().length(), preview);

        if (response.statusCode() != 200) {
            log.warn("harvestFromSource() | HuggingFace API returned status {}",
                     response.statusCode());
            return List.of();
        }

        List<HuggingFaceModelResponse> models = objectMapper.readValue(
                response.body(), new TypeReference<>() {});

        List<NewsArticle> articles = new ArrayList<>();
        int limit = Math.min(models.size(), source.maxItems());
        for (int i = 0; i < limit; i++) {
            HuggingFaceModelResponse model = models.get(i);
            Instant modelStart = Instant.now();
            NewsArticle article = mapModelToArticle(model, source);
            Instant modelEnd = Instant.now();
            long modelMs = java.time.Duration.between(modelStart, modelEnd).toMillis();
            if (article != null) {
                articles.add(article);
                log.debug("harvestFromSource() | MODEL-MAPPED modelId={} elapsedMs={} bodyPreview={}",
                          model.modelId(), modelMs,
                          article.bodyText().length() > 200
                                  ? article.bodyText().substring(0, 200) + "..." : article.bodyText());
            }
        }

        log.debug("harvestFromSource() | return={} articles from '{}'",
                  articles.size(), source.name());
        return articles;
    }

    /**
     * Maps a single HuggingFace model to a {@link NewsArticle}.
     */
    private NewsArticle mapModelToArticle(HuggingFaceModelResponse model,
                                          FeedSourceConfig source) {
        log.debug("mapModelToArticle() | modelId={}", model.modelId());

        if (model.modelId() == null || model.modelId().isBlank()) {
            log.warn("mapModelToArticle() | skipping model with null/blank modelId");
            return null;
        }

        String articleId = "hf-" + model.modelId();
        String title = model.modelId();
        URI url = URI.create("https://huggingface.co/" + model.modelId());

        StringBuilder bodyBuilder = new StringBuilder();
        bodyBuilder.append("Model: ").append(model.modelId());
        if (model.author() != null) {
            bodyBuilder.append("\nAuthor: ").append(model.author());
        }
        if (model.pipeline_tag() != null) {
            bodyBuilder.append("\nPipeline: ").append(model.pipeline_tag());
        }
        bodyBuilder.append("\nDownloads: ").append(model.downloads());
        if (model.tags() != null && !model.tags().isEmpty()) {
            bodyBuilder.append("\nTags: ");
            for (int i = 0; i < model.tags().size(); i++) {
                if (i > 0) {
                    bodyBuilder.append(", ");
                }
                bodyBuilder.append(model.tags().get(i));
            }
        }

        String bodyText = bodyBuilder.toString();
        if (bodyText.length() > MAX_BODY_LENGTH) {
            bodyText = bodyText.substring(0, MAX_BODY_LENGTH);
        }

        String author = model.author() != null ? model.author() : source.name();

        Instant publishedAt = Instant.now();
        if (model.lastModified() != null) {
            try {
                publishedAt = Instant.parse(model.lastModified());
            } catch (Exception ex) {
                log.debug("mapModelToArticle() | could not parse lastModified '{}', using now",
                          model.lastModified());
            }
        }

        NewsArticle article = new NewsArticle(
                articleId,
                title,
                url,
                bodyText,
                source.effectiveTopic(),
                author,
                source.topicId(),
                source.name(),
                source.tier().name(),
                source.baseWeight(),
                publishedAt
        );

        log.debug("mapModelToArticle() | return={}", article.title());
        return article;
    }
}

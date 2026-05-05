package com.wgblackmon.aihealthcare.infrastructure.research;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RetrievalQuery;
import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SourceRetrievalPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SourceRetrievalPort} adapter that serves the LEGACY_GOOGLE pipeline path.
 *
 * <p>Retrieves source documents by delegating to the existing article-ingestion
 * database via {@link ArticleIngestionPort#fetchAllByTopic(String)}, then converts
 * each {@link NewsArticle} into a uniform {@link RetrievedSource} record.
 *
 * <p>This adapter requires no external API keys and is always available.  It acts as
 * the safe fallback in {@link com.wgblackmon.aihealthcare.domain.service.ResearchOrchestratorService}
 * when the STAGED_RESEARCH path yields no results.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>{@code articleId} → {@code sourceId}</li>
 *   <li>{@code title} → {@code title}</li>
 *   <li>{@code url.toString()} → {@code url}</li>
 *   <li>{@code bodyText} → {@code snippet} (blank is preserved)</li>
 *   <li>Engine is always {@code "GOOGLE"}</li>
 *   <li>{@code publishedAt} → {@code retrievedAt}; {@code Instant.now()} when null</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
@Slf4j
@Component
public class LegacyGoogleResearchAdapter implements SourceRetrievalPort {

    private final ArticleIngestionPort articleIngestionPort;

    /**
     * Constructs the adapter with its article-ingestion port dependency.
     *
     * @param articleIngestionPort Port for fetching stored articles by topic.
     */
    public LegacyGoogleResearchAdapter(ArticleIngestionPort articleIngestionPort) {
        log.debug("LegacyGoogleResearchAdapter() | articleIngestionPort={}",
                  articleIngestionPort.getClass().getSimpleName());
        this.articleIngestionPort = articleIngestionPort;
        log.debug("LegacyGoogleResearchAdapter() | return=void");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Uses {@code query.text()} as the topic filter.  The {@code maxResults} cap is
     * applied after fetching so that the ingestion port's own pagination is not required.
     */
    @Override
    public List<RetrievedSource> retrieve(RetrievalQuery query) {
        log.debug("retrieve() | query={}", query);

        List<NewsArticle> articles = articleIngestionPort.fetchAllByTopic(query.text());
        log.info("retrieve() | fetchAllByTopic('{}') returned {} articles",
                 query.text(), articles.size());

        List<RetrievedSource> result = new ArrayList<>();
        int limit = query.maxResults();
        for (int i = 0; i < articles.size() && i < limit; i++) {
            NewsArticle article = articles.get(i);
            result.add(toRetrievedSource(article));
        }

        log.debug("retrieve() | return={} sources (capped from {} articles)",
                  result.size(), articles.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Private mapping
    // -------------------------------------------------------------------------

    private RetrievedSource toRetrievedSource(NewsArticle article) {
        log.debug("toRetrievedSource() | articleId={}", article.articleId());

        String url = article.url() != null ? article.url().toString() : "";
        String snippet = article.bodyText() != null ? article.bodyText() : "";
        Instant retrievedAt = article.publishedAt() != null
                ? article.publishedAt()
                : Instant.now();

        RetrievedSource result = new RetrievedSource(
                article.articleId(),
                article.title(),
                url,
                snippet,
                "GOOGLE",
                retrievedAt);

        log.debug("toRetrievedSource() | return=RetrievedSource[sourceId={}]", result.sourceId());
        return result;
    }
}

package com.wgblackmon.aihealthcare.infrastructure.research;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RetrievalQuery;
import com.wgblackmon.aihealthcare.domain.model.RetrievedSource;
import com.wgblackmon.aihealthcare.domain.port.outbound.SourceRetrievalPort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity.PerplexityHarvester;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SourceRetrievalPort} adapter that backs the STAGED_RESEARCH pipeline with the
 * Perplexity Sonar API via the existing {@link PerplexityHarvester} stub.
 *
 * <p>When {@code PERPLEXITY_API_KEY} is not configured, {@link PerplexityHarvester}
 * returns an empty list — this adapter propagates that empty result without error.
 * The orchestrator detects the empty result and falls back to the legacy adapter,
 * ensuring the pipeline always completes successfully.
 *
 * <p>Full Perplexity Sonar API integration (HTTP call, JSON parsing, source URL
 * extraction) is encapsulated in {@link PerplexityHarvester}.  When that integration
 * slice is implemented, this adapter requires no changes — only the harvester changes.
 *
 * <p>Mapping rules:
 * <ul>
 *   <li>{@code articleId} → {@code sourceId}</li>
 *   <li>{@code title} → {@code title}</li>
 *   <li>{@code url.toString()} → {@code url}</li>
 *   <li>{@code bodyText} → {@code snippet}</li>
 *   <li>Engine is always {@code "PERPLEXITY"}</li>
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
public class PerplexityResearchAdapter implements SourceRetrievalPort {

    private final PerplexityHarvester perplexityHarvester;

    /**
     * Constructs the adapter with its Perplexity harvester dependency.
     *
     * @param perplexityHarvester The Perplexity harvester stub (or full implementation).
     */
    public PerplexityResearchAdapter(PerplexityHarvester perplexityHarvester) {
        log.debug("PerplexityResearchAdapter() | perplexityHarvester={}",
                  perplexityHarvester.getClass().getSimpleName());
        this.perplexityHarvester = perplexityHarvester;
        log.debug("PerplexityResearchAdapter() | return=void");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link PerplexityHarvester#harvestArticles(String)} using
     * {@code query.text()} as the topic.  Returns an empty list when no API key is set.
     */
    @Override
    public List<RetrievedSource> retrieve(RetrievalQuery query) {
        log.debug("retrieve() | query={}", query);

        List<NewsArticle> articles = perplexityHarvester.harvestArticles(query.text());
        log.info("retrieve() | harvestArticles('{}') returned {} articles",
                 query.text(), articles.size());

        List<RetrievedSource> result = new ArrayList<>();
        int limit = query.maxResults();
        for (int i = 0; i < articles.size() && i < limit; i++) {
            result.add(toRetrievedSource(articles.get(i)));
        }

        log.debug("retrieve() | return={} sources", result.size());
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
                "PERPLEXITY",
                retrievedAt);

        log.debug("toRetrievedSource() | return=RetrievedSource[sourceId={}]", result.sourceId());
        return result;
    }
}

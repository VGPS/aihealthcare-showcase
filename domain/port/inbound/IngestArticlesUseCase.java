package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.time.LocalDate;
import java.util.List;

/**
 * Inbound port — drive the article ingestion process.
 *
 * <p>The implementation lives in {@code application} and orchestrates calls to
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort}.
 * The {@code web} layer calls this interface; it never touches the application
 * service class directly.
 */
public interface IngestArticlesUseCase {

    /**
     * Ingest articles for the supplied topics and associate them with a run.
     *
     * @param runId              Unique identifier for this ingestion run.
     * @param weekOf             The Monday of the newsletter week being prepared.
     * @param topics             Search keywords/phrases to drive article discovery.
     * @param maxArticlesPerTopic Maximum number of articles to fetch per topic.
     * @return All articles successfully ingested across all topics.
     */
    List<NewsArticle> ingest(
            String runId,
            LocalDate weekOf,
            List<String> topics,
            int maxArticlesPerTopic
    );
}

package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TopicSummary;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TopicSummaryPort;

import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Domain service that generates concise 3-sentence AI summaries for each
 * topic section on the news listing page.
 *
 * <p>For each topic with more than one harvested article, delegates to
 * {@link AiSummarizationPort#generateTopicSummary} and persists the result
 * via {@link TopicSummaryPort#save}.  Topics with zero or one article are
 * silently skipped.  AI failures are caught and logged so that one failing
 * topic does not prevent summaries for the remaining topics.
 *
 * <p>This service is wired as a Spring bean in {@code AppConfig} but has
 * no Spring dependencies itself (domain-layer purity).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-21
 * @updated 2026-05-21
 */
public class TopicSummaryGenerationService {

    private static final Logger LOG =
            Logger.getLogger(TopicSummaryGenerationService.class.getName());

    private final AiSummarizationPort aiPort;
    private final TopicSummaryPort summaryPort;
    private final ArticleIngestionPort ingestionPort;

    public TopicSummaryGenerationService(AiSummarizationPort aiPort,
                                         TopicSummaryPort summaryPort,
                                         ArticleIngestionPort ingestionPort) {
        LOG.fine(() -> String.format("TopicSummaryGenerationService() | aiPort=%s, summaryPort=%s, ingestionPort=%s",
                aiPort.getClass().getSimpleName(),
                summaryPort.getClass().getSimpleName(),
                ingestionPort.getClass().getSimpleName()));
        this.aiPort = aiPort;
        this.summaryPort = summaryPort;
        this.ingestionPort = ingestionPort;
    }

    /**
     * Generate and persist 3-sentence summaries for all topics that have
     * more than one article.
     *
     * @param topicNames ordered list of topic names to process
     */
    public void generateSummaries(List<String> topicNames) {
        LOG.fine(() -> String.format("generateSummaries() | topicNames=%s", topicNames));

        for (String topic : topicNames) {
            List<NewsArticle> articles = ingestionPort.fetchAllByTopic(topic);

            if (articles.size() <= 1) {
                LOG.fine(() -> String.format("generateSummaries() | skipping topic=%s (articleCount=%d)",
                        topic, articles.size()));
                continue;
            }

            try {
                String summaryText = aiPort.generateTopicSummary(topic, articles);
                TopicSummary summary = new TopicSummary(topic, summaryText, Instant.now());
                summaryPort.save(summary);
                LOG.info(String.format("generateSummaries() | saved summary for topic=%s (articleCount=%d)",
                        topic, articles.size()));
            } catch (Exception e) {
                LOG.log(Level.WARNING,
                        String.format("generateSummaries() | AI summary failed for topic=%s", topic), e);
            }
        }

        LOG.fine("generateSummaries() | return=void");
    }
}

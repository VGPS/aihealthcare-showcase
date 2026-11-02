package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.TopicSummary;
import com.wgblackmon.aihealthcare.domain.model.TopicSummaryResult;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.TopicSummaryPort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * <p>Topics with an existing summary are also skipped when none of their
 * articles have been published since that summary was generated — this is
 * the cost-discipline freshness gate (see {@code CLAUDE.md} "Production LLM
 * Cost Discipline"). When no article in the topic carries a known
 * {@code publishedAt} (common for some RSS feeds), freshness cannot be
 * determined and the summary is regenerated anyway, erring toward staying
 * current rather than silently going stale forever.
 *
 * <p>This service is wired as a Spring bean in {@code AppConfig} but has
 * no Spring dependencies itself (domain-layer purity).
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-05-21
 * @updated 2026-11-02
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

            Optional<TopicSummary> existing = summaryPort.findByTopic(topic);
            if (existing.isPresent() && !hasArticlesNewerThan(articles, existing.get().generatedAt())) {
                LOG.fine(() -> String.format(
                        "generateSummaries() | skipping topic=%s — no new articles since last summary at %s",
                        topic, existing.get().generatedAt()));
                continue;
            }

            try {
                TopicSummaryResult result = aiPort.generateTopicSummary(topic, articles);
                TopicSummary summary = new TopicSummary(
                        topic, result.text(), Instant.now(),
                        result.pipelineVersion(), result.lintScore());
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

    /**
     * Returns {@code true} when at least one article was published after
     * {@code since}, or when freshness cannot be determined because no
     * article carries a non-null {@code publishedAt} (safe default: assume
     * fresh rather than silently suppress summaries forever).
     *
     * @param articles candidate articles for the topic
     * @param since    timestamp the last summary was generated
     */
    private boolean hasArticlesNewerThan(List<NewsArticle> articles, Instant since) {
        boolean anyDated = false;
        for (NewsArticle article : articles) {
            Instant publishedAt = article.publishedAt();
            if (publishedAt == null) {
                continue;
            }
            anyDated = true;
            if (publishedAt.isAfter(since)) {
                return true;
            }
        }
        return !anyDated;
    }
}

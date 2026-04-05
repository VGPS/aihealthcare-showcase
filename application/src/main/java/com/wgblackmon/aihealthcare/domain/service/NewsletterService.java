package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.exception.NoArticlesFoundException;
import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterDraft;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Application service that implements both inbound use-case ports for Slice 1.
 *
 * <p>All state is held in-memory for Slice 1 — no persistence layer yet.
 * Slice 2 will introduce JPA and replace the in-memory maps with repository calls.
 *
 * <p>This class is intentionally free of Spring annotations; the {@code web} module
 * wires it via a {@code @Bean} factory method so the application layer stays
 * framework-free and trivially testable without a Spring context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2025-01-27
 * @updated 2026-04-04
 */
@Slf4j
public class NewsletterService implements IngestArticlesUseCase, GenerateNewsletterUseCase {

    private final ArticleIngestionPort ingestionPort;
    private final AiSummarizationPort  summarizationPort;

    // In-memory stores — replaced by repositories in Slice 2
    private final Map<String, List<NewsArticle>> articlesByRunId = new HashMap<>();
    private final Map<String, NewsletterDraft>   draftByDraftId  = new HashMap<>();

    private final AtomicInteger sectionCounter = new AtomicInteger(0);

    public NewsletterService(ArticleIngestionPort ingestionPort,
                             AiSummarizationPort summarizationPort) {
        log.debug("NewsletterService() | ingestionPort={}, summarizationPort={}",
                  ingestionPort, summarizationPort);
        this.ingestionPort     = ingestionPort;
        this.summarizationPort = summarizationPort;
    }

    // -------------------------------------------------------------------------
    // IngestArticlesUseCase
    // -------------------------------------------------------------------------

    @Override
    public List<NewsArticle> ingest(String runId,
                                    LocalDate weekOf,
                                    List<String> topics,
                                    int maxArticlesPerTopic) {
        log.debug("ingest() | runId={}, weekOf={}, topicCount={}, maxArticlesPerTopic={}",
                  runId, weekOf, topics == null ? 0 : topics.size(), maxArticlesPerTopic);

        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (topics == null || topics.isEmpty()) {
            throw new IllegalArgumentException("topics must contain at least one entry");
        }
        if (maxArticlesPerTopic < 1) {
            throw new IllegalArgumentException("maxArticlesPerTopic must be >= 1");
        }

        List<NewsArticle> all = new ArrayList<>();
        for (String topic : topics) {
            List<NewsArticle> fetched = ingestionPort.fetchArticles(topic, maxArticlesPerTopic);
            for (NewsArticle article : fetched) {
                all.add(article);
            }
        }

        articlesByRunId.put(runId, all);

        List<NewsArticle> result = List.copyOf(all);
        log.info("ingest() | Ingestion complete: runId={}, articleCount={}", runId, result.size());
        log.debug("ingest() | return={} articles", result.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // GenerateNewsletterUseCase
    // -------------------------------------------------------------------------

    @Override
    public NewsletterDraft generate(String runId,
                                    String draftId,
                                    String newsletterTitle,
                                    NewsletterTone tone,
                                    int maxSectionsPerTopic) {
        log.debug("generate() | runId={}, draftId={}, newsletterTitle={}, tone={}, maxSectionsPerTopic={}",
                  runId, draftId, newsletterTitle, tone, maxSectionsPerTopic);

        List<NewsArticle> articles = articlesByRunId.get(runId);
        if (articles == null) {
            throw new RunNotFoundException(runId);
        }
        if (articles.isEmpty()) {
            throw new NoArticlesFoundException(runId);
        }

        // Group articles by topic using a plain loop
        Map<String, List<NewsArticle>> byTopic = new HashMap<>();
        for (NewsArticle article : articles) {
            List<NewsArticle> bucket = byTopic.computeIfAbsent(article.topic(), k -> new ArrayList<>());
            bucket.add(article);
        }

        // Summarize each topic group into one section
        List<NewsletterSection> sections = new ArrayList<>();
        for (Map.Entry<String, List<NewsArticle>> entry : byTopic.entrySet()) {
            List<NewsArticle> topicArticles = entry.getValue();
            int cap = Math.min(topicArticles.size(), maxSectionsPerTopic);
            String sectionId = "section-%03d".formatted(sectionCounter.incrementAndGet());
            NewsletterSection section = summarizationPort.summarize(
                    topicArticles.subList(0, cap),
                    entry.getKey(),
                    tone,
                    sectionId
            );
            sections.add(section);
        }

        String introduction = summarizationPort.generateIntroduction(sections, tone);

        NewsletterDraft result = new NewsletterDraft(
                draftId,
                runId,
                newsletterTitle,
                LocalDate.now(),
                introduction,
                sections,
                articles,
                Instant.now()
        );

        draftByDraftId.put(draftId, result);
        log.info("generate() | Draft generated: draftId={}, sectionCount={}", draftId, sections.size());
        log.debug("generate() | return={}", result);
        return result;
    }

    /**
     * Retrieve a previously generated draft by ID.
     *
     * @param draftId The draft identifier to look up.
     * @return The matching {@link NewsletterDraft}.
     * @throws RunNotFoundException if no draft exists for {@code draftId}.
     */
    public NewsletterDraft getDraft(String draftId) {
        log.debug("getDraft() | draftId={}", draftId);

        NewsletterDraft result = draftByDraftId.get(draftId);
        if (result == null) {
            throw new RunNotFoundException(draftId);
        }

        log.debug("getDraft() | return={}", result);
        return result;
    }
}

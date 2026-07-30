package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.exception.NoArticlesFoundException;
import com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterDraft;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.WikiQueryPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Application service that implements both inbound use-case ports.
 *
 * <p>All article state is held in-memory for the duration of a request cycle
 * (the {@code articlesByRunId} map).  Slice 2 introduces JPA persistence for
 * articles via {@link ArticleIngestionPort}; the map remains for transient
 * ingest → generate handoff within a single server session.
 *
 * <p>When {@link #generate} completes, the resulting {@link NewsletterDraft}
 * is rendered to HTML and plain text by {@link NewsletterRenderer}, then saved
 * as a {@link NewsletterRun} via {@link NewsletterRunPort} for the subscriber
 * archive.  The in-memory {@code draftByDraftId} map is also retained so that
 * {@link #getDraft} can return the full draft (with sections and articles) in
 * the same server session without a round-trip to the DB.
 *
 * <p>This class carries no Spring annotations; {@code AppConfig} wires it as a
 * {@code @Bean} so the application layer remains framework-free and trivially
 * testable without a Spring context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2025-01-27
 * @updated 2026-07-05
 */
@Slf4j
public class NewsletterService implements IngestArticlesUseCase, GenerateNewsletterUseCase {

    private final ArticleIngestionPort         ingestionPort;
    private final AiSummarizationPort          summarizationPort;
    private final NewsletterRenderer           renderer;
    private final NewsletterRunPort            newsletterRunPort;
    private final ArticleSearchPort            searchPort;
    private final WikiQueryPort                wikiQueryPort;
    private final ReversalWatchSectionBuilder  reversalWatchBuilder;
    private final LegalBriefSectionBuilder    legalBriefBuilder;

    // In-memory stores — articles map supports ingest→generate handoff;
    // draftByDraftId supports getDraft() in the same session.
    private final Map<String, List<NewsArticle>> articlesByRunId = new HashMap<>();
    private final Map<String, NewsletterDraft>   draftByDraftId  = new HashMap<>();

    private final AtomicInteger sectionCounter = new AtomicInteger(0);

    public NewsletterService(ArticleIngestionPort ingestionPort,
                             AiSummarizationPort summarizationPort,
                             NewsletterRenderer renderer,
                             NewsletterRunPort newsletterRunPort,
                             ArticleSearchPort searchPort,
                             WikiQueryPort wikiQueryPort,
                             ReversalWatchSectionBuilder reversalWatchBuilder,
                             LegalBriefSectionBuilder legalBriefBuilder) {
        log.debug("NewsletterService() | ingestionPort={}, summarizationPort={}, renderer={}, newsletterRunPort={}, searchPort={}, wikiQueryPort={}, reversalWatchBuilder={}, legalBriefBuilder={}",
                  ingestionPort, summarizationPort, renderer, newsletterRunPort, searchPort, wikiQueryPort, reversalWatchBuilder, legalBriefBuilder);
        this.ingestionPort         = ingestionPort;
        this.summarizationPort     = summarizationPort;
        this.renderer              = renderer;
        this.newsletterRunPort     = newsletterRunPort;
        this.searchPort            = searchPort;
        this.wikiQueryPort         = wikiQueryPort;
        this.reversalWatchBuilder  = reversalWatchBuilder;
        this.legalBriefBuilder     = legalBriefBuilder;
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
                                    int maxSectionsPerTopic,
                                    boolean ragEnabled,
                                    int ragContextCount) {
        log.debug("generate() | runId={}, draftId={}, newsletterTitle={}, tone={}, maxSectionsPerTopic={}, ragEnabled={}, ragContextCount={}",
                  runId, draftId, newsletterTitle, tone, maxSectionsPerTopic, ragEnabled, ragContextCount);

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
            List<NewsArticle> freshSlice = topicArticles.subList(0, cap);
            String sectionId = "section-%03d".formatted(sectionCounter.incrementAndGet());

            NewsletterSection section;
            if (ragEnabled) {
                List<NewsArticle> candidates = searchPort.findSimilar(entry.getKey(), ragContextCount);
                // De-duplicate: exclude articles already present in the fresh batch
                Set<String> freshIds = new HashSet<>();
                for (NewsArticle a : freshSlice) {
                    freshIds.add(a.articleId());
                }
                List<NewsArticle> contextArticles = new ArrayList<>();
                for (NewsArticle candidate : candidates) {
                    if (!freshIds.contains(candidate.articleId())) {
                        contextArticles.add(candidate);
                    }
                }
                log.debug("generate() | RAG context: topic={}, freshCount={}, contextCount={}",
                          entry.getKey(), freshSlice.size(), contextArticles.size());
                section = summarizationPort.summarizeWithContext(
                        freshSlice, contextArticles, entry.getKey(), tone, sectionId);
            } else {
                section = summarizationPort.summarize(freshSlice, entry.getKey(), tone, sectionId);
            }
            sections.add(section);
        }

        // Append Reversal Watch section from recent wiki contradictions (last 7 days)
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        List<Contradiction> contradictions = wikiQueryPort.recentContradictions(since);
        String reversalSectionId = "section-%03d".formatted(sectionCounter.incrementAndGet());
        NewsletterSection reversalSection = reversalWatchBuilder.build(contradictions, reversalSectionId);
        if (reversalSection != null) {
            sections.add(reversalSection);
            log.info("generate() | Reversal Watch section appended with {} contradiction(s)",
                    contradictions.size());
        }

        // Append Legal & Regulatory Brief section from recent legal data (last 7 days)
        String legalSectionId = "section-%03d".formatted(sectionCounter.incrementAndGet());
        NewsletterSection legalSection = legalBriefBuilder.build(legalSectionId, 7);
        if (legalSection != null) {
            sections.add(legalSection);
            log.info("generate() | Legal Brief section appended");
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

        // Render and persist the run for the subscriber archive
        String html      = renderer.renderHtml(result);
        String plainText = renderer.renderPlainText(result);
        NewsletterRun run = new NewsletterRun(
                draftId,
                newsletterTitle,
                LocalDate.now(),
                html,
                plainText,
                NewsletterRunStatus.DRAFT,
                Instant.now()
        );
        newsletterRunPort.save(run);

        // Retain in-memory for same-session getDraft() calls
        draftByDraftId.put(draftId, result);

        log.info("generate() | Draft generated and persisted: draftId={}, sectionCount={}",
                 draftId, sections.size());
        log.debug("generate() | return={}", result);
        return result;
    }

    /**
     * Retrieve a previously generated draft by ID (same session only).
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

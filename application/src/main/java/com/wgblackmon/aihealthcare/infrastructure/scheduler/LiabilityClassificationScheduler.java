package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.LiabilityIncident;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.LiabilityClassificationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.LiabilityIncidentPort;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Daily scheduler that scans recently harvested LEGAL-tier articles and proposes
 * new {@link LiabilityIncident} records via {@link LiabilityClassificationPort}.
 *
 * <p>Each article is checked against existing incidents by {@code sourceUrl}
 * before classification to prevent duplicates. Proposed incidents are saved with
 * {@code status = PENDING_REVIEW} for admin approval at {@code /admin/liability}.
 *
 * <p>Processing is capped at {@code aihealthcare.liability.max-articles-per-run}
 * articles per execution to bound LLM token spend.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-11-15
 * @updated 2026-11-15
 */
@Slf4j
@Component
public class LiabilityClassificationScheduler {

    private final NewsArticleRepository      articleRepository;
    private final LiabilityIncidentPort      incidentPort;
    private final LiabilityClassificationPort classificationPort;
    private final int                        lookbackDays;
    private final int                        maxArticlesPerRun;

    public LiabilityClassificationScheduler(
            NewsArticleRepository articleRepository,
            LiabilityIncidentPort incidentPort,
            LiabilityClassificationPort classificationPort,
            @Value("${aihealthcare.liability.lookback-days:7}") int lookbackDays,
            @Value("${aihealthcare.liability.max-articles-per-run:20}") int maxArticlesPerRun) {
        log.debug("LiabilityClassificationScheduler() | lookbackDays={}, maxArticlesPerRun={}",
                  lookbackDays, maxArticlesPerRun);
        this.articleRepository  = articleRepository;
        this.incidentPort       = incidentPort;
        this.classificationPort = classificationPort;
        this.lookbackDays       = lookbackDays;
        this.maxArticlesPerRun  = maxArticlesPerRun;
    }

    @Scheduled(cron = "${aihealthcare.liability.classification-cron:0 45 6 * * ?}")
    public void scanAndPropose() {
        log.info("scanAndPropose() | starting LEGAL-tier scan, lookbackDays={}, max={}",
                 lookbackDays, maxArticlesPerRun);

        Instant cutoff = Instant.now().minus(lookbackDays, ChronoUnit.DAYS);
        List<NewsArticleEntity> candidates = articleRepository
                .findBySourceTierAndCreatedAtAfter("LEGAL", cutoff);

        log.info("scanAndPropose() | found {} LEGAL articles since cutoff", candidates.size());

        int processed = 0;
        int proposed  = 0;
        int skipped   = 0;

        for (NewsArticleEntity entity : candidates) {
            if (processed >= maxArticlesPerRun) {
                log.info("scanAndPropose() | reached maxArticlesPerRun={}, stopping", maxArticlesPerRun);
                break;
            }

            String url = entity.getUrl();
            if (url == null || incidentPort.existsBySourceUrl(url)) {
                skipped++;
                continue;
            }

            NewsArticle article = toArticle(entity);
            java.util.Optional<LiabilityIncident> proposal = classificationPort.classify(article);
            proposal.ifPresent(incident -> {
                incidentPort.save(incident);
                log.info("scanAndPropose() | proposed incident: title={}, type={}",
                         incident.title(), incident.incidentType());
            });
            if (proposal.isPresent()) proposed++;
            processed++;
        }

        log.info("scanAndPropose() | return=void, processed={}, proposed={}, skipped={}",
                 processed, proposed, skipped);
    }

    private NewsArticle toArticle(NewsArticleEntity e) {
        URI url = null;
        try {
            if (e.getUrl() != null) url = URI.create(e.getUrl());
        } catch (IllegalArgumentException ex) {
            log.warn("toArticle() | invalid URI for articleId={}", e.getArticleId());
        }
        return new NewsArticle(
                e.getArticleId(),
                e.getTitle(),
                url,
                e.getBodyText(),
                e.getTopic(),
                e.getAuthor(),
                e.getTopicId(),
                e.getSourceName(),
                e.getSourceTier(),
                e.getSourceWeight(),
                e.getPublishedAt()
        );
    }
}

package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestArticlesUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled job that generates a daily newsletter draft from the previous
 * day's harvested articles.
 *
 * <p>Runs on the cron schedule defined by {@code aihealthcare.newsletter.schedule}
 * in {@code application.yml} (default: daily at midnight UTC).  On each tick the
 * scheduler performs ingest and generation only — it does <b>not</b> auto-send:
 * <ol>
 *   <li>Calls {@link IngestArticlesUseCase#ingest} to load the previous day's
 *       persisted articles from the database into the service session.</li>
 *   <li>Calls {@link GenerateNewsletterUseCase#generate} to summarize the
 *       articles and persist a {@code NewsletterRun} in {@code DRAFT} status.</li>
 * </ol>
 *
 * <p>The generated draft is available for review and editing at
 * {@code /newsletter/runs/{runId}/edit} (TinyMCE WYSIWYG editor).  Delivery
 * to subscribers is triggered manually via the "Send" button on that page,
 * which calls {@code DeliverNewsletterUseCase.deliver()}.
 *
 * <p>The class depends only on inbound-port interfaces so the application's
 * hexagonal boundary is preserved — it drives the domain but knows nothing of
 * repositories, SMTP, or feed adapters.
 *
 * <p>Runtime identifiers ({@code runId}, {@code draftId}) are derived from
 * {@code Instant.now().toEpochMilli()}.  No external ID generator is required.
 *
 * <p>If any step throws, the exception is caught and logged at {@code ERROR}
 * so the scheduler thread survives and the next scheduled tick can still run.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-04-16
 * @updated 2026-05-22
 */
@Slf4j
@Component
public class NewsletterGenerationScheduler {

    private final IngestArticlesUseCase     ingestUseCase;
    private final GenerateNewsletterUseCase generateUseCase;

    private final String         title;
    private final NewsletterTone tone;
    private final String         topic;
    private final int            maxArticlesPerTopic;
    private final int            maxSectionsPerTopic;

    public NewsletterGenerationScheduler(
            IngestArticlesUseCase ingestUseCase,
            GenerateNewsletterUseCase generateUseCase,
            @Value("${aihealthcare.newsletter.title:AI in Healthcare Weekly}") String title,
            @Value("${aihealthcare.newsletter.tone:PROFESSIONAL}") NewsletterTone tone,
            @Value("${aihealthcare.newsletter.topic:AI Healthcare}") String topic,
            @Value("${aihealthcare.newsletter.max-articles-per-topic:20}") int maxArticlesPerTopic,
            @Value("${aihealthcare.newsletter.max-sections-per-topic:3}") int maxSectionsPerTopic) {
        log.debug("NewsletterGenerationScheduler() | title={}, tone={}, topic={}, maxArticlesPerTopic={}, maxSectionsPerTopic={}",
                  title, tone, topic, maxArticlesPerTopic, maxSectionsPerTopic);
        this.ingestUseCase       = ingestUseCase;
        this.generateUseCase     = generateUseCase;
        this.title               = title;
        this.tone                = tone;
        this.topic               = topic;
        this.maxArticlesPerTopic = maxArticlesPerTopic;
        this.maxSectionsPerTopic = maxSectionsPerTopic;
    }

    /**
     * Daily tick — runs the ingest → generate pipeline to create a DRAFT.
     *
     * <p>Invoked by Spring on the schedule configured by
     * {@code aihealthcare.newsletter.schedule}.  The draft is NOT auto-sent;
     * review it at {@code /newsletter/runs/{runId}/edit} and send manually.
     * Failures at any stage are logged and swallowed so the scheduler thread
     * remains alive for the next scheduled run.
     */
    @Scheduled(cron = "${aihealthcare.newsletter.schedule}")
    public void runDailyDraftGeneration() {
        log.debug("runDailyDraftGeneration() | starting daily newsletter draft pipeline");

        long      nowMs   = Instant.now().toEpochMilli();
        String    runId   = "run-" + nowMs;
        String    draftId = "draft-" + nowMs;
        LocalDate weekOf  = LocalDate.now();

        try {
            log.info("runDailyDraftGeneration() | Pipeline starting: runId={}, draftId={}, weekOf={}",
                     runId, draftId, weekOf);

            ingestUseCase.ingest(runId, weekOf, List.of(topic), maxArticlesPerTopic);
            generateUseCase.generate(runId, draftId, title, tone, maxSectionsPerTopic, false, 3);

            log.info("runDailyDraftGeneration() | Draft ready for review: draftId={} — edit at /newsletter/runs/{}/edit",
                     draftId, runId);
        } catch (Exception ex) {
            log.error("runDailyDraftGeneration() | Pipeline failed: {}", ex.getMessage(), ex);
        }

        log.debug("runDailyDraftGeneration() | return=void");
    }
}

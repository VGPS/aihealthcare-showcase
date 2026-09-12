package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.GenerateNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.IngestArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterAutoSendPort;
import com.wgblackmon.aihealthcare.infrastructure.config.NewsTopicProperties;
import com.wgblackmon.aihealthcare.web.controller.DisplayFormats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Scheduled job that generates a weekly newsletter draft from the trailing
 * week's harvested articles.
 *
 * <p>Runs on the cron schedule defined by {@code aihealthcare.newsletter.schedule}
 * in {@code application.yml} (default: Monday 08:00 UTC).  On each tick the
 * scheduler performs ingest, generation, and auto-delivery:
 * <ol>
 *   <li>Calls {@link IngestArticlesUseCase#ingest} once per configured topic in
 *       {@link NewsTopicProperties} (the same 19-topic list the news listing page
 *       uses) to load the trailing week's persisted articles into the service
 *       session. Iterating the full topic list — rather than a single
 *       substring-matched keyword — ensures every feed group is represented,
 *       not just the ones whose label happens to contain that keyword.</li>
 *   <li>Calls {@link GenerateNewsletterUseCase#generate} to summarize the
 *       articles and persist a {@code NewsletterRun} in {@code DRAFT} status.</li>
 *   <li>Checks the auto-send override via {@link NewsletterAutoSendPort} — if
 *       overridden for today, the draft is left for manual review.</li>
 *   <li>Otherwise, calls {@link DeliverNewsletterUseCase#deliver} to send
 *       the newsletter to ENTERPRISE/SUBSCRIBER/DEMO subscribers.</li>
 * </ol>
 *
 * <p>The FREE-tier digest is <b>not</b> part of this pipeline — see
 * {@code DigestDeliveryScheduler}, which sends it daily on its own schedule.
 *
 * <p>The "Override Auto Send Today" checkbox on the Newsletter Runs page
 * ({@code /newsletter/runs}) lets admins suppress auto-send for a specific day.
 * Manual send via the edit page resets the override.
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
 * @version 3.0
 * @since   2026-04-16
 * @updated 2026-09-12
 */
@Slf4j
@Component
public class NewsletterGenerationScheduler {

    private static final DateTimeFormatter WEEK_OF_FMT = DisplayFormats.LONG_DATE;

    private final IngestArticlesUseCase     ingestUseCase;
    private final GenerateNewsletterUseCase generateUseCase;
    private final DeliverNewsletterUseCase  deliverUseCase;
    private final NewsletterAutoSendPort    autoSendPort;
    private final NewsTopicProperties       newsTopicProperties;

    private final String         title;
    private final NewsletterTone tone;
    private final int            maxArticlesPerTopic;
    private final int            maxSectionsPerTopic;

    public NewsletterGenerationScheduler(
            IngestArticlesUseCase ingestUseCase,
            GenerateNewsletterUseCase generateUseCase,
            @Autowired(required = false) DeliverNewsletterUseCase deliverUseCase,
            @Autowired(required = false) NewsletterAutoSendPort autoSendPort,
            NewsTopicProperties newsTopicProperties,
            @Value("${aihealthcare.newsletter.title:AI in Healthcare Weekly}") String title,
            @Value("${aihealthcare.newsletter.tone:PROFESSIONAL}") NewsletterTone tone,
            @Value("${aihealthcare.newsletter.max-articles-per-topic:20}") int maxArticlesPerTopic,
            @Value("${aihealthcare.newsletter.max-sections-per-topic:3}") int maxSectionsPerTopic) {
        log.debug("NewsletterGenerationScheduler() | title={}, tone={}, maxArticlesPerTopic={}, maxSectionsPerTopic={}",
                  title, tone, maxArticlesPerTopic, maxSectionsPerTopic);
        this.ingestUseCase       = ingestUseCase;
        this.generateUseCase     = generateUseCase;
        this.deliverUseCase      = deliverUseCase;
        this.autoSendPort        = autoSendPort;
        this.newsTopicProperties = newsTopicProperties;
        this.title               = title;
        this.tone                = tone;
        this.maxArticlesPerTopic = maxArticlesPerTopic;
        this.maxSectionsPerTopic = maxSectionsPerTopic;
    }

    /**
     * Weekly tick — runs the ingest → generate → deliver pipeline.
     *
     * <p>Invoked by Spring on the schedule configured by
     * {@code aihealthcare.newsletter.schedule}.  After generating the draft,
     * checks the auto-send override: if overridden for today, the draft is
     * left for manual review; otherwise it is auto-sent to ENTERPRISE/
     * SUBSCRIBER/DEMO subscribers. Failures at any stage are logged and
     * swallowed so the scheduler thread remains alive for the next scheduled run.
     */
    @Scheduled(cron = "${aihealthcare.newsletter.schedule}")
    public void runWeeklyDraftGeneration() {
        log.debug("runWeeklyDraftGeneration() | starting weekly newsletter pipeline");

        long      nowMs   = Instant.now().toEpochMilli();
        String    runId   = "run-" + nowMs;
        String    draftId = "draft-" + nowMs;
        LocalDate weekOf  = LocalDate.now();
        List<String> topics = newsTopicProperties.getTopics();

        try {
            log.info("runWeeklyDraftGeneration() | Pipeline starting: runId={}, draftId={}, weekOf={}, topicCount={}",
                     runId, draftId, weekOf, topics.size());

            ingestUseCase.ingest(runId, weekOf, topics, maxArticlesPerTopic);
            String dateStampedTitle = title + " — Week of " + weekOf.format(WEEK_OF_FMT);
            generateUseCase.generate(runId, draftId, dateStampedTitle, tone, maxSectionsPerTopic, false, 3);

            log.info("runWeeklyDraftGeneration() | Draft generated: runId={}", runId);

            // Auto-send unless overridden for today
            if (autoSendPort != null && autoSendPort.isOverriddenForDate(weekOf)) {
                log.info("runWeeklyDraftGeneration() | Auto-send overridden for {} — draft left for manual review at /newsletter/runs/{}/edit",
                         weekOf, draftId);
            } else if (deliverUseCase != null) {
                int count = deliverUseCase.deliver(draftId);
                log.info("runWeeklyDraftGeneration() | Newsletter auto-sent to {} recipients: draftId={}", count, draftId);
            } else {
                log.warn("runWeeklyDraftGeneration() | DeliverNewsletterUseCase not available — draft created but not sent");
            }
        } catch (Exception ex) {
            log.error("runWeeklyDraftGeneration() | Pipeline failed: {}", ex.getMessage(), ex);
        }

        log.debug("runWeeklyDraftGeneration() | return=void");
    }
}

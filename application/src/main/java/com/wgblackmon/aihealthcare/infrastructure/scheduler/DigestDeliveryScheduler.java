package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.PipelinePausePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that sends the daily digest to all active subscribers.
 *
 * <p>Runs on the cron schedule defined by {@code aihealthcare.digest.schedule}
 * in {@code application.yml} (default: daily at midnight UTC). Deliberately
 * decoupled from {@link NewsletterGenerationScheduler} (the paid newsletter's
 * weekly cron) — the digest is built and sent fresh on every tick via
 * {@link DeliverNewsletterUseCase#deliverDigest()}, which internally calls
 * {@code DigestNewsletterRenderer.buildDigest()} and does not depend on any
 * ingest/generate step or on the paid newsletter's "Override Auto Send Today"
 * setting.
 *
 * <p>Before building/sending, checks the {@link PipelinePausePort} kill switch
 * (key {@code "digest-send"}, toggled from the Pipeline Management admin page).
 * When paused, the tick is skipped entirely — this is a sticky operator pause,
 * not a date-scoped override, since the digest has no persisted draft to review
 * before resuming.
 *
 * <p>If delivery throws, the exception is caught and logged at {@code ERROR}
 * so the scheduler thread survives and the next scheduled tick can still run.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-24
 * @updated 2026-10-16
 */
@Slf4j
@Component
public class DigestDeliveryScheduler {

    static final String PAUSE_KEY = "digest-send";

    private final DeliverNewsletterUseCase deliverUseCase;
    private final PipelinePausePort pausePort;

    public DigestDeliveryScheduler(DeliverNewsletterUseCase deliverUseCase,
                                   PipelinePausePort pausePort) {
        log.debug("DigestDeliveryScheduler() | deliverUseCase={}, pausePort={}",
                  deliverUseCase.getClass().getSimpleName(), pausePort.getClass().getSimpleName());
        this.deliverUseCase = deliverUseCase;
        this.pausePort = pausePort;
    }

    /**
     * Daily tick — builds and sends the digest to all active subscribers.
     *
     * <p>Invoked by Spring on the schedule configured by
     * {@code aihealthcare.digest.schedule}. Failures are logged and swallowed
     * so the scheduler thread remains alive for the next scheduled run.
     */
    @Scheduled(cron = "${aihealthcare.digest.schedule}")
    public void runDailyDigestDelivery() {
        log.debug("runDailyDigestDelivery() | starting daily digest delivery");

        if (pausePort.isPaused(PAUSE_KEY)) {
            log.info("runDailyDigestDelivery() | Digest send paused via admin kill switch — skipping tick");
            log.debug("runDailyDigestDelivery() | return=void (paused)");
            return;
        }

        try {
            int count = deliverUseCase.deliverDigest();
            log.info("runDailyDigestDelivery() | Digest sent to {} recipients", count);
        } catch (Exception ex) {
            log.error("runDailyDigestDelivery() | Digest delivery failed: {}", ex.getMessage(), ex);
        }

        log.debug("runDailyDigestDelivery() | return=void");
    }
}

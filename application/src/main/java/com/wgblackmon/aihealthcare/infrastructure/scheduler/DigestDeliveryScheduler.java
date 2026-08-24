package com.wgblackmon.aihealthcare.infrastructure.scheduler;

import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled job that sends the FREE-tier daily digest.
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
 * <p>If delivery throws, the exception is caught and logged at {@code ERROR}
 * so the scheduler thread survives and the next scheduled tick can still run.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-24
 * @updated 2026-08-24
 */
@Slf4j
@Component
public class DigestDeliveryScheduler {

    private final DeliverNewsletterUseCase deliverUseCase;

    public DigestDeliveryScheduler(DeliverNewsletterUseCase deliverUseCase) {
        log.debug("DigestDeliveryScheduler() | deliverUseCase={}", deliverUseCase.getClass().getSimpleName());
        this.deliverUseCase = deliverUseCase;
    }

    /**
     * Daily tick — builds and sends the FREE-tier digest.
     *
     * <p>Invoked by Spring on the schedule configured by
     * {@code aihealthcare.digest.schedule}. Failures are logged and swallowed
     * so the scheduler thread remains alive for the next scheduled run.
     */
    @Scheduled(cron = "${aihealthcare.digest.schedule}")
    public void runDailyDigestDelivery() {
        log.debug("runDailyDigestDelivery() | starting daily digest delivery");

        try {
            int count = deliverUseCase.deliverDigest();
            log.info("runDailyDigestDelivery() | Digest sent to {} recipients", count);
        } catch (Exception ex) {
            log.error("runDailyDigestDelivery() | Digest delivery failed: {}", ex.getMessage(), ex);
        }

        log.debug("runDailyDigestDelivery() | return=void");
    }
}

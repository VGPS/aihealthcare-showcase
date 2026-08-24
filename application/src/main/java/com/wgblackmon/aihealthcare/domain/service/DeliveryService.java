package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.exception.DuplicateSubscriberException;
import com.wgblackmon.aihealthcare.domain.exception.SubscriberNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSubscribersUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service that implements subscriber lifecycle management.
 *
 * <p>Orchestrates the subscriber domain operations — add, remove, list — by
 * coordinating through {@link SubscriberPort}.  All business rules live here:
 * duplicate detection before adding, existence check before removing.
 *
 * <p>This class carries no Spring annotations; {@link
 * com.wgblackmon.aihealthcare.infrastructure.config.AppConfig} wires it as a
 * {@code @Bean} so the application layer stays framework-free and is trivially
 * testable with plain Mockito, no Spring context required.
 *
 * <p>Slice 3c will add scheduling support via {@code NewsletterGenerationScheduler}.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-04-13
 * @updated 2026-08-24
 */
@Slf4j
public class DeliveryService implements ManageSubscribersUseCase, DeliverNewsletterUseCase {

    private final SubscriberPort             subscriberPort;
    private final NewsletterRunPort          newsletterRunPort;
    private final NewsletterDeliveryPort     newsletterDeliveryPort;
    private final NewsletterTeaserBuilder    teaserBuilder;
    private final DigestNewsletterRenderer   digestRenderer;

    public DeliveryService(SubscriberPort subscriberPort,
                           NewsletterRunPort newsletterRunPort,
                           NewsletterDeliveryPort newsletterDeliveryPort,
                           NewsletterTeaserBuilder teaserBuilder,
                           DigestNewsletterRenderer digestRenderer) {
        log.debug("DeliveryService() | subscriberPort={}, newsletterRunPort={}, newsletterDeliveryPort={}, teaserBuilder={}, digestRenderer={}",
                  subscriberPort.getClass().getSimpleName(),
                  newsletterRunPort.getClass().getSimpleName(),
                  newsletterDeliveryPort.getClass().getSimpleName(),
                  teaserBuilder.getClass().getSimpleName(),
                  digestRenderer.getClass().getSimpleName());
        this.subscriberPort         = subscriberPort;
        this.newsletterRunPort      = newsletterRunPort;
        this.newsletterDeliveryPort = newsletterDeliveryPort;
        this.teaserBuilder          = teaserBuilder;
        this.digestRenderer         = digestRenderer;
    }

    // -------------------------------------------------------------------------
    // DeliverNewsletterUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Retrieves the run via {@link NewsletterRunPort} (throws
     * {@link com.wgblackmon.aihealthcare.domain.exception.RunNotFoundException}
     * if absent), collects all active subscribers, dispatches via
     * {@link NewsletterDeliveryPort}, then updates the run status to
     * {@link NewsletterRunStatus#SENT}.
     *
     * <p>If there are no active subscribers the delivery is skipped and the
     * run status is left unchanged; a warning is logged.
     */
    @Override
    public int deliver(String runId) {
        log.debug("deliver() | runId={}", runId);

        NewsletterRun run = newsletterRunPort.findByRunId(runId);

        // Split subscribers by tier — ENTERPRISE/SUBSCRIBER/DEMO get the full newsletter.
        // FREE subscribers get the digest instead — see deliverDigest(), which runs on
        // its own schedule and is not part of this call.
        List<Subscriber> enterpriseRecipients = subscriberPort.findAllActiveByTier(SubscriptionTier.ENTERPRISE);
        List<Subscriber> subscriberRecipients = subscriberPort.findAllActiveByTier(SubscriptionTier.SUBSCRIBER);
        List<Subscriber> demoRecipients = subscriberPort.findAllActiveByTier(SubscriptionTier.DEMO);

        int totalRecipients = enterpriseRecipients.size() + subscriberRecipients.size() + demoRecipients.size();
        if (totalRecipients == 0) {
            log.warn("deliver() | No active subscribers — skipping delivery for runId={}", runId);
            log.debug("deliver() | return=0");
            return 0;
        }

        int sentCount = 0;

        // Deliver full newsletter to ENTERPRISE subscribers
        if (!enterpriseRecipients.isEmpty()) {
            newsletterDeliveryPort.deliver(run, enterpriseRecipients);
            sentCount += enterpriseRecipients.size();
            log.info("deliver() | Full newsletter sent to {} enterprise-tier recipients", enterpriseRecipients.size());
        }

        // Deliver full newsletter to SUBSCRIBER subscribers
        if (!subscriberRecipients.isEmpty()) {
            newsletterDeliveryPort.deliver(run, subscriberRecipients);
            sentCount += subscriberRecipients.size();
            log.info("deliver() | Full newsletter sent to {} subscriber-tier recipients", subscriberRecipients.size());
        }

        // Deliver full newsletter to DEMO subscribers (same content as SUBSCRIBER)
        if (!demoRecipients.isEmpty()) {
            newsletterDeliveryPort.deliver(run, demoRecipients);
            sentCount += demoRecipients.size();
            log.info("deliver() | Full newsletter sent to {} demo-tier recipients", demoRecipients.size());
        }

        if (sentCount > 0) {
            NewsletterRun sent = new NewsletterRun(
                    run.runId(),
                    run.title(),
                    run.weekOf(),
                    run.htmlContent(),
                    run.plainTextContent(),
                    NewsletterRunStatus.SENT,
                    run.generatedAt()
            );
            newsletterRunPort.save(sent);
            log.info("deliver() | Newsletter delivered: runId={}, enterprise={}, subscriber={}, demo={}",
                     runId, enterpriseRecipients.size(), subscriberRecipients.size(), demoRecipients.size());
        } else {
            log.info("deliver() | No emails sent for runId={} — 0 recipients available", runId);
        }

        log.debug("deliver() | return={}", sentCount);
        return sentCount;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Builds a fresh digest via {@link DigestNewsletterRenderer} and sends it to
     * all active FREE-tier subscribers. Runs independently of {@link #deliver(String)}
     * so the FREE digest's daily cadence is not tied to the paid newsletter's schedule.
     */
    @Override
    public int deliverDigest() {
        log.debug("deliverDigest() | (no args)");

        List<Subscriber> freeRecipients = subscriberPort.findAllActiveByTier(SubscriptionTier.FREE);
        if (freeRecipients.isEmpty()) {
            log.info("deliverDigest() | No active FREE subscribers — skipping digest");
            log.debug("deliverDigest() | return=0");
            return 0;
        }

        Optional<NewsletterRun> digestOpt = digestRenderer.buildDigest();
        if (digestOpt.isEmpty()) {
            log.info("deliverDigest() | No articles today — skipping digest for {} free subscribers", freeRecipients.size());
            log.debug("deliverDigest() | return=0");
            return 0;
        }

        newsletterDeliveryPort.deliver(digestOpt.get(), freeRecipients);
        log.info("deliverDigest() | Digest newsletter sent to {} free subscribers", freeRecipients.size());

        log.debug("deliverDigest() | return={}", freeRecipients.size());
        return freeRecipients.size();
    }

    // -------------------------------------------------------------------------
    // ManageSubscribersUseCase
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Checks for an existing subscription with the same email before persisting.
     * Throws {@link DuplicateSubscriberException} if one is found.
     */
    @Override
    public Subscriber addSubscriber(String email, String name) {
        log.debug("addSubscriber() | email={}, name={}", email, name);

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }

        Optional<Subscriber> existing = subscriberPort.findByEmail(email);
        if (existing.isPresent()) {
            log.warn("addSubscriber() | Duplicate subscription attempt for email={}", LogSanitizer.maskEmail(email));
            throw new DuplicateSubscriberException(email);
        }

        Subscriber subscriber = new Subscriber(email, name, true, Instant.now(), null,
                UUID.randomUUID().toString(), null, null);
        subscriberPort.save(subscriber);

        log.info("addSubscriber() | Subscriber added: email={}", LogSanitizer.maskEmail(email));
        log.debug("addSubscriber() | return={}", subscriber);
        return subscriber;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Verifies the subscriber exists before delegating deletion to the port.
     * Throws {@link SubscriberNotFoundException} if the email is not found.
     */
    @Override
    public void removeSubscriber(String email) {
        log.debug("removeSubscriber() | email={}", email);

        Optional<Subscriber> existing = subscriberPort.findByEmail(email);
        if (existing.isEmpty()) {
            log.warn("removeSubscriber() | No subscriber found for email={}", LogSanitizer.maskEmail(email));
            throw new SubscriberNotFoundException(email);
        }

        subscriberPort.deleteByEmail(email);

        log.info("removeSubscriber() | Subscriber removed: email={}", LogSanitizer.maskEmail(email));
        log.debug("removeSubscriber() | return=void");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Subscriber> listSubscribers() {
        log.debug("listSubscribers() | (no args)");

        List<Subscriber> result = subscriberPort.findAll();

        log.debug("listSubscribers() | return={} subscribers", result.size());
        return result;
    }
}

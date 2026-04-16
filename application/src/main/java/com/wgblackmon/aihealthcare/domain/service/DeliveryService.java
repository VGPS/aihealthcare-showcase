package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.exception.DuplicateSubscriberException;
import com.wgblackmon.aihealthcare.domain.exception.SubscriberNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.port.inbound.DeliverNewsletterUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSubscribersUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterRunPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
@Slf4j
public class DeliveryService implements ManageSubscribersUseCase, DeliverNewsletterUseCase {

    private final SubscriberPort          subscriberPort;
    private final NewsletterRunPort       newsletterRunPort;
    private final NewsletterDeliveryPort  newsletterDeliveryPort;

    public DeliveryService(SubscriberPort subscriberPort,
                           NewsletterRunPort newsletterRunPort,
                           NewsletterDeliveryPort newsletterDeliveryPort) {
        log.debug("DeliveryService() | subscriberPort={}, newsletterRunPort={}, newsletterDeliveryPort={}",
                  subscriberPort.getClass().getSimpleName(),
                  newsletterRunPort.getClass().getSimpleName(),
                  newsletterDeliveryPort.getClass().getSimpleName());
        this.subscriberPort         = subscriberPort;
        this.newsletterRunPort      = newsletterRunPort;
        this.newsletterDeliveryPort = newsletterDeliveryPort;
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
    public void deliver(String runId) {
        log.debug("deliver() | runId={}", runId);

        NewsletterRun run = newsletterRunPort.findByRunId(runId);

        List<Subscriber> recipients = subscriberPort.findAllActive();
        if (recipients.isEmpty()) {
            log.warn("deliver() | No active subscribers — skipping delivery for runId={}", runId);
            log.debug("deliver() | return=void (no recipients)");
            return;
        }

        newsletterDeliveryPort.deliver(run, recipients);

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

        log.info("deliver() | Newsletter delivered: runId={}, recipientCount={}", runId, recipients.size());
        log.debug("deliver() | return=void");
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
            log.warn("addSubscriber() | Duplicate subscription attempt for email={}", email);
            throw new DuplicateSubscriberException(email);
        }

        Subscriber subscriber = new Subscriber(email, name, true, Instant.now());
        subscriberPort.save(subscriber);

        log.info("addSubscriber() | Subscriber added: email={}", email);
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
            log.warn("removeSubscriber() | No subscriber found for email={}", email);
            throw new SubscriberNotFoundException(email);
        }

        subscriberPort.deleteByEmail(email);

        log.info("removeSubscriber() | Subscriber removed: email={}", email);
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

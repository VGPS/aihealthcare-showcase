package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.service.DigestNewsletterRenderer;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin REST controller for sending the free-tier digest newsletter to a
 * specific address for review and QA purposes.
 *
 * <p>{@code POST /monitoring/digest-newsletter/send?email=xxx} builds today's
 * digest and delivers it to the given address regardless of subscription tier.
 *
 * @author  Bill Blackmon
 * @since   2026-08-15
 * @updated 2026-08-15
 */
@Slf4j
@RestController
@RequestMapping("/monitoring/digest-newsletter")
public class DigestNewsletterController {

    private final DigestNewsletterRenderer renderer;
    private final NewsletterDeliveryPort   deliveryPort;
    private final SubscriberPort           subscriberPort;

    public DigestNewsletterController(DigestNewsletterRenderer renderer,
                                      NewsletterDeliveryPort deliveryPort,
                                      SubscriberPort subscriberPort) {
        log.debug("DigestNewsletterController() | renderer={}, deliveryPort={}, subscriberPort={}",
                renderer.getClass().getSimpleName(),
                deliveryPort.getClass().getSimpleName(),
                subscriberPort.getClass().getSimpleName());
        this.renderer      = renderer;
        this.deliveryPort  = deliveryPort;
        this.subscriberPort = subscriberPort;
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestParam String email) {
        log.debug("send() | email={}", LogSanitizer.maskEmail(email));

        Optional<NewsletterRun> run = renderer.buildDigest();
        if (run.isEmpty()) {
            log.info("send() | return=400 (no articles today)");
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No articles available for digest — nothing ingested in the last day"));
        }

        Optional<Subscriber> existing = subscriberPort.findByEmail(email);
        Subscriber recipient = existing.orElseGet(() ->
                new Subscriber(email, email, true, java.time.Instant.now(), null, null, null, null));

        deliveryPort.deliver(run.get(), List.of(recipient));
        log.info("send() | Digest newsletter sent to {}", LogSanitizer.maskEmail(email));

        log.debug("send() | return=200");
        return ResponseEntity.ok(Map.of("sent", true, "email", email,
                "articleCount", run.get().title()));
    }
}

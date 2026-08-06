package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.port.outbound.NewsletterDeliveryPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.service.SampleNewsletterRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin REST controller for previewing and sending the sample newsletter
 * used for cold outreach and prospect acquisition.
 *
 * <p>{@code GET /monitoring/sample-newsletter} returns the rendered HTML
 * for browser preview. {@code POST /monitoring/sample-newsletter/send}
 * sends it to a specific email address.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
@Slf4j
@RestController
@RequestMapping("/monitoring/sample-newsletter")
public class SampleNewsletterController {

    private final SampleNewsletterRenderer renderer;
    private final NewsletterDeliveryPort deliveryPort;
    private final SubscriberPort subscriberPort;

    public SampleNewsletterController(SampleNewsletterRenderer renderer,
                                       NewsletterDeliveryPort deliveryPort,
                                       SubscriberPort subscriberPort) {
        log.debug("SampleNewsletterController() | renderer={}, deliveryPort={}, subscriberPort={}",
                renderer.getClass().getSimpleName(),
                deliveryPort.getClass().getSimpleName(),
                subscriberPort.getClass().getSimpleName());
        this.renderer = renderer;
        this.deliveryPort = deliveryPort;
        this.subscriberPort = subscriberPort;
    }

    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> preview() {
        log.debug("preview() | (no args)");

        Optional<NewsletterRun> run = renderer.buildSample();
        if (run.isEmpty()) {
            log.debug("preview() | return=204 (no articles)");
            return ResponseEntity.noContent().build();
        }

        log.debug("preview() | return=200 ({} chars)", run.get().htmlContent().length());
        return ResponseEntity.ok(run.get().htmlContent());
    }

    @PostMapping("/send")
    public ResponseEntity<?> send(@RequestParam String email) {
        log.debug("send() | email={}", email);

        Optional<NewsletterRun> run = renderer.buildSample();
        if (run.isEmpty()) {
            log.debug("send() | return=400 (no articles)");
            return ResponseEntity.badRequest().body(Map.of("error", "No articles available for sample newsletter"));
        }

        Optional<Subscriber> existingSub = subscriberPort.findByEmail(email);
        Subscriber recipient;
        if (existingSub.isPresent()) {
            recipient = existingSub.get();
        } else {
            recipient = new Subscriber(email, email, true,
                    java.time.Instant.now(), null, null, null, null);
        }

        List<Subscriber> recipients = new ArrayList<>();
        recipients.add(recipient);

        deliveryPort.deliver(run.get(), recipients);
        log.info("send() | Sample newsletter sent to {}", email);

        log.debug("send() | return=200");
        return ResponseEntity.ok(Map.of("sent", true, "email", email));
    }
}

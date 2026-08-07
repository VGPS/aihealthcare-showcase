package com.wgblackmon.aihealthcare.web.controller;

import com.stripe.exception.StripeException;
import com.stripe.model.billingportal.Session;
import com.stripe.param.billingportal.SessionCreateParams;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Creates Stripe Billing Portal sessions so subscribers can manage their
 * subscription (cancel, update payment method, view invoices).
 *
 * <p>{@code POST /api/v1/stripe/portal} accepts a subscriber email, looks up
 * their Stripe customer ID, creates a portal session, and returns the URL.
 * The caller redirects to the portal URL.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-31
 * @updated 2026-08-07
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/stripe/portal")
public class StripePortalController {

    private final StripeProperties stripeProperties;
    private final SubscriberPort subscriberPort;
    private final String baseUrl;

    public StripePortalController(StripeProperties stripeProperties,
                                  SubscriberPort subscriberPort,
                                  @Value("${aihealthcare.base-url}") String baseUrl) {
        log.debug("StripePortalController() | stripeProperties={}, subscriberPort={}, baseUrl={}",
                  stripeProperties, subscriberPort.getClass().getSimpleName(), baseUrl);
        this.stripeProperties = stripeProperties;
        this.subscriberPort = subscriberPort;
        this.baseUrl = baseUrl;
    }

    /**
     * Creates a Stripe Billing Portal session and returns the portal URL.
     *
     * @param body map containing "email" key
     * @return JSON with "portalUrl" key, or error status
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createPortalSession(@RequestBody Map<String, String> body) {
        log.debug("createPortalSession() | body={}", body);

        if (!stripeProperties.isEnabled()) {
            log.warn("createPortalSession() | Stripe not configured");
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "Stripe is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(err);
        }

        String email = body.getOrDefault("email", "").trim();
        if (email.isBlank()) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "email is required");
            return ResponseEntity.badRequest().body(err);
        }

        Optional<Subscriber> subOpt = subscriberPort.findByEmail(email);
        if (subOpt.isEmpty() || subOpt.get().stripeCustomerId() == null || subOpt.get().stripeCustomerId().isBlank()) {
            log.warn("createPortalSession() | No Stripe customer ID for email={}", LogSanitizer.maskEmail(email));
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "No Stripe subscription found for this account");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
        }

        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setCustomer(subOpt.get().stripeCustomerId())
                    .setReturnUrl(baseUrl + "/profile")
                    .build();
            Session portalSession = Session.create(params);

            Map<String, String> result = new LinkedHashMap<>();
            result.put("portalUrl", portalSession.getUrl());
            log.debug("createPortalSession() | return=portalUrl");
            return ResponseEntity.ok(result);
        } catch (StripeException e) {
            log.error("createPortalSession() | Stripe API error: {}", e.getMessage());
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "Failed to create portal session");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }
}

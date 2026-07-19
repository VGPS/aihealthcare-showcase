package com.wgblackmon.aihealthcare.web.controller;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import com.wgblackmon.aihealthcare.web.dto.CheckoutRequest;
import com.wgblackmon.aihealthcare.web.dto.CheckoutResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates Stripe Checkout Sessions for paid newsletter subscriptions.
 *
 * <p>{@code POST /api/v1/stripe/checkout} accepts a subscriber email and a
 * Stripe Price ID, creates a Stripe-hosted payment page, and returns the
 * checkout URL.  The caller (frontend or API client) redirects the subscriber
 * to that URL to complete payment.
 *
 * <p>After successful payment, Stripe fires a {@code checkout.session.completed}
 * webhook event handled by {@link StripeWebhookController}, which updates the
 * subscriber's tier locally.
 *
 * <p>The Price ID is passed as session metadata so the webhook can map it
 * to a {@link com.wgblackmon.aihealthcare.domain.model.SubscriptionTier}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-23
 * @updated 2026-07-18
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/stripe")
public class StripeCheckoutController {

    private final StripeProperties stripeProperties;
    private final String baseUrl;

    public StripeCheckoutController(StripeProperties stripeProperties,
                                    @Value("${aihealthcare.base-url}") String baseUrl) {
        log.debug("StripeCheckoutController() | stripeEnabled={}, baseUrl={}", stripeProperties.isEnabled(), baseUrl);
        this.stripeProperties = stripeProperties;
        this.baseUrl = baseUrl;
    }

    /**
     * Creates a Stripe Checkout Session and returns the hosted payment page URL.
     *
     * @param request Body containing {@code email} and {@code priceId}.
     * @return 200 with {@link CheckoutResponse} containing the checkout URL;
     *         400 if email or priceId is blank; 503 if Stripe is not configured;
     *         500 if the Stripe API call fails.
     */
    @PostMapping("/checkout")
    public ResponseEntity<?> createCheckoutSession(@RequestBody CheckoutRequest request) {
        log.debug("createCheckoutSession() | email={}, priceId={}", request.email(), request.priceId());

        if (!stripeProperties.isEnabled()) {
            log.warn("createCheckoutSession() | Stripe is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                 .body("Stripe integration not configured");
        }

        if (request.email() == null || request.email().isBlank()) {
            log.warn("createCheckoutSession() | email is blank");
            return ResponseEntity.badRequest().body("email is required");
        }

        if (request.priceId() == null || request.priceId().isBlank()) {
            log.warn("createCheckoutSession() | priceId is blank");
            return ResponseEntity.badRequest().body("priceId is required");
        }

        try {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("price_id", request.priceId());
            metadata.put("customer_email", request.email());

            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomerEmail(request.email())
                    .setSuccessUrl(baseUrl + "/dashboard?checkout=success")
                    .setCancelUrl(baseUrl + "/dashboard?checkout=cancelled")
                    .putAllMetadata(metadata)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(request.priceId())
                            .setQuantity(1L)
                            .build())
                    .build();

            Session session = Session.create(params);

            CheckoutResponse response = new CheckoutResponse(session.getUrl());
            log.info("createCheckoutSession() | Checkout session created: email={}, sessionId={}",
                     request.email(), session.getId());
            log.debug("createCheckoutSession() | return={}", response);
            return ResponseEntity.ok(response);

        } catch (StripeException e) {
            log.error("createCheckoutSession() | Stripe API error: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Stripe error: " + e.getMessage());
        }
    }
}

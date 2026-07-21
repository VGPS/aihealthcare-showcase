package com.wgblackmon.aihealthcare.infrastructure.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for Stripe Billing integration.
 *
 * <p>Maps the {@code aihealthcare.stripe.*} keys from {@code application.yml}
 * into a type-safe bean.  Values are typically set via environment variables:
 * <ul>
 *   <li>{@code STRIPE_API_KEY} — Stripe secret key (sk_test_... or sk_live_...)</li>
 *   <li>{@code STRIPE_WEBHOOK_SECRET} — webhook endpoint signing secret (whsec_...)</li>
 *   <li>{@code STRIPE_SUBSCRIBER_PRICE_ID} — Stripe Price ID for the Subscriber tier</li>
 * </ul>
 *
 * <p>When {@code apiKey} is blank the Stripe integration is effectively disabled;
 * the webhook controller will reject incoming events gracefully.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-05-23
 * @updated 2026-07-20
 */
@Slf4j
@Component
@ConfigurationProperties(prefix = "aihealthcare.stripe")
public class StripeProperties {

    private String apiKey = "";
    private String publishableKey = "";
    private String webhookSecret = "";
    private String subscriberPriceId = "";

    /**
     * Initializes the Stripe SDK global API key if configured.
     * Called automatically by Spring after property binding.
     */
    @PostConstruct
    void init() {
        if (!apiKey.isBlank()) {
            Stripe.apiKey = apiKey;
            log.info("init() | Stripe API key configured (test={})", apiKey.startsWith("sk_test_"));
        } else {
            log.info("init() | Stripe API key not set — Stripe integration disabled");
        }
        log.debug("init() | return=void");
    }

    public String getApiKey()                            { return apiKey; }
    public void setApiKey(String apiKey)                  { this.apiKey = apiKey; }

    public String getPublishableKey()                              { return publishableKey; }
    public void setPublishableKey(String publishableKey)           { this.publishableKey = publishableKey; }

    public String getWebhookSecret()                     { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret)   { this.webhookSecret = webhookSecret; }

    public String getSubscriberPriceId()                       { return subscriberPriceId; }
    public void setSubscriberPriceId(String subscriberPriceId) { this.subscriberPriceId = subscriberPriceId; }

    /**
     * Returns {@code true} if a Stripe API key has been configured.
     *
     * @return whether Stripe integration is enabled.
     */
    public boolean isEnabled() {
        log.debug("isEnabled() | apiKey.blank={}", apiKey.isBlank());
        return !apiKey.isBlank();
    }
}

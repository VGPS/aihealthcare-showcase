package com.wgblackmon.aihealthcare.web.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;

/**
 * Receives Stripe webhook events and updates local subscriber tiers.
 *
 * <p>Stripe sends events to {@code POST /api/v1/stripe/webhook} whenever a
 * subscription changes.  This controller verifies the webhook signature,
 * extracts the customer email and price ID, maps the price to a
 * {@link SubscriptionTier}, and upserts the local {@link Subscriber} record.
 *
 * <p>Handled event types:
 * <ul>
 *   <li>{@code checkout.session.completed} — new paid subscriber</li>
 *   <li>{@code customer.subscription.updated} — upgrade or downgrade</li>
 *   <li>{@code customer.subscription.deleted} — cancellation → tier reverts to FREE</li>
 * </ul>
 *
 * <p>To test locally, install the Stripe CLI and run:
 * <pre>
 *   stripe listen --forward-to localhost:8080/api/v1/stripe/webhook
 * </pre>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-23
 * @updated 2026-07-20
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/stripe")
public class StripeWebhookController {

    private final StripeProperties stripeProperties;
    private final SubscriberPort   subscriberPort;
    private final AppUserPort      appUserPort;

    public StripeWebhookController(StripeProperties stripeProperties,
                                   SubscriberPort subscriberPort,
                                   AppUserPort appUserPort) {
        log.debug("StripeWebhookController() | stripeEnabled={}, appUserPort={}",
                  stripeProperties.isEnabled(), appUserPort.getClass().getSimpleName());
        this.stripeProperties = stripeProperties;
        this.subscriberPort   = subscriberPort;
        this.appUserPort      = appUserPort;
    }

    /**
     * Receives a Stripe webhook event, verifies the signature, and dispatches
     * to the appropriate handler based on event type.
     *
     * @param payload           Raw JSON body from Stripe.
     * @param stripeSignature   {@code Stripe-Signature} header for verification.
     * @return 200 OK on success, 400 on bad signature, 500 on processing error.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {
        log.debug("handleWebhook() | payloadLength={}, signaturePresent={}",
                  payload.length(), stripeSignature != null);

        if (!stripeProperties.isEnabled()) {
            log.warn("handleWebhook() | Stripe is not configured — rejecting webhook");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                 .body("Stripe integration not configured");
        }

        Event event;
        if (stripeProperties.getWebhookSecret() != null
                && !stripeProperties.getWebhookSecret().isBlank()) {
            try {
                event = Webhook.constructEvent(payload, stripeSignature,
                                               stripeProperties.getWebhookSecret());
            } catch (SignatureVerificationException e) {
                log.warn("handleWebhook() | Signature verification failed: {}", e.getMessage());
                return ResponseEntity.badRequest().body("Invalid signature");
            }
        } else {
            log.warn("handleWebhook() | No webhook secret configured — skipping signature verification");
            event = Event.GSON.fromJson(payload, Event.class);
        }

        String eventType = event.getType();
        log.info("handleWebhook() | Received event: type={}, id={}", eventType, event.getId());

        switch (eventType) {
            case "checkout.session.completed":
                handleCheckoutCompleted(event);
                break;
            case "customer.subscription.updated":
                handleSubscriptionUpdated(event);
                break;
            case "customer.subscription.deleted":
                handleSubscriptionDeleted(event);
                break;
            default:
                log.debug("handleWebhook() | Ignoring unhandled event type={}", eventType);
                break;
        }

        log.debug("handleWebhook() | return=200 OK");
        return ResponseEntity.ok("Received");
    }

    // -------------------------------------------------------------------------
    // Event handlers
    // -------------------------------------------------------------------------

    private void handleCheckoutCompleted(Event event) {
        log.debug("handleCheckoutCompleted() | eventId={}", event.getId());

        Session session = (Session) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        if (session == null) {
            log.warn("handleCheckoutCompleted() | Could not deserialize session");
            return;
        }

        String email = session.getCustomerEmail();
        if (email == null || email.isBlank()) {
            log.warn("handleCheckoutCompleted() | No customer email in session");
            return;
        }

        SubscriptionTier tier = mapPriceToTier(session.getMetadata() != null
                ? session.getMetadata().get("price_id") : null);

        upsertSubscriber(email, tier);
        reEnableAppUser(email, tier);
        log.info("handleCheckoutCompleted() | New paid subscriber: email={}, tier={}", email, tier);
        log.debug("handleCheckoutCompleted() | return=void");
    }

    private void handleSubscriptionUpdated(Event event) {
        log.debug("handleSubscriptionUpdated() | eventId={}", event.getId());

        Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        if (subscription == null) {
            log.warn("handleSubscriptionUpdated() | Could not deserialize subscription");
            return;
        }

        String email = extractEmailFromSubscription(subscription);
        if (email == null) {
            log.warn("handleSubscriptionUpdated() | Could not determine email");
            return;
        }

        String priceId = null;
        if (subscription.getItems() != null && subscription.getItems().getData() != null
                && !subscription.getItems().getData().isEmpty()) {
            priceId = subscription.getItems().getData().get(0).getPrice().getId();
        }

        SubscriptionTier tier = mapPriceToTier(priceId);
        upsertSubscriber(email, tier);
        log.info("handleSubscriptionUpdated() | Subscription changed: email={}, tier={}", email, tier);
        log.debug("handleSubscriptionUpdated() | return=void");
    }

    private void handleSubscriptionDeleted(Event event) {
        log.debug("handleSubscriptionDeleted() | eventId={}", event.getId());

        Subscription subscription = (Subscription) event.getDataObjectDeserializer()
                .getObject().orElse(null);
        if (subscription == null) {
            log.warn("handleSubscriptionDeleted() | Could not deserialize subscription");
            return;
        }

        String email = extractEmailFromSubscription(subscription);
        if (email == null) {
            log.warn("handleSubscriptionDeleted() | Could not determine email");
            return;
        }

        upsertSubscriber(email, SubscriptionTier.FREE);
        log.info("handleSubscriptionDeleted() | Subscription cancelled: email={}, reverted to FREE", email);
        log.debug("handleSubscriptionDeleted() | return=void");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Re-enables an {@link AppUser} account and sets its tier after successful
     * Stripe checkout.  Handles the FREE→SUBSCRIBER upgrade path where the
     * user's account was previously disabled during demo expiration.
     */
    private void reEnableAppUser(String email, SubscriptionTier tier) {
        log.debug("reEnableAppUser() | email={}, tier={}", email, tier);

        Optional<AppUser> userOpt = appUserPort.findByEmail(email);
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            AppUser updated = new AppUser(user.email(), user.passwordHash(), user.displayName(),
                    user.role(), true, tier, user.demoExpiresAt());
            appUserPort.save(updated);
            log.info("reEnableAppUser() | Re-enabled app_user: email={}, tier={}", email, tier);
        } else {
            log.debug("reEnableAppUser() | No app_user found for email={} — skipping", email);
        }

        log.debug("reEnableAppUser() | return=void");
    }

    /**
     * Maps a Stripe Price ID to a {@link SubscriptionTier}.
     * Falls back to FREE if the price ID is unrecognised or null.
     */
    private SubscriptionTier mapPriceToTier(String priceId) {
        log.debug("mapPriceToTier() | priceId={}", priceId);

        SubscriptionTier result;
        if (priceId != null && priceId.equals(stripeProperties.getSubscriberPriceId())) {
            result = SubscriptionTier.SUBSCRIBER;
        } else {
            result = SubscriptionTier.FREE;
        }

        log.debug("mapPriceToTier() | return={}", result);
        return result;
    }

    /**
     * Creates or updates a subscriber's tier.  If the subscriber does not
     * exist locally, a new record is created with the email as the name.
     */
    private void upsertSubscriber(String email, SubscriptionTier tier) {
        log.debug("upsertSubscriber() | email={}, tier={}", email, tier);

        Optional<Subscriber> existing = subscriberPort.findByEmail(email);
        Subscriber updated;
        if (existing.isPresent()) {
            Subscriber s = existing.get();
            updated = new Subscriber(s.email(), s.name(), s.active(), s.subscribedAt(), tier);
        } else {
            updated = new Subscriber(email, email, true, Instant.now(), tier);
        }
        subscriberPort.save(updated);

        log.debug("upsertSubscriber() | return=void");
    }

    /**
     * Extracts the customer email from a Subscription object's metadata.
     * Stripe subscriptions carry the customer email in metadata if set during
     * checkout, otherwise we fall back to null.
     */
    private String extractEmailFromSubscription(Subscription subscription) {
        log.debug("extractEmailFromSubscription() | subscriptionId={}", subscription.getId());

        String email = null;
        if (subscription.getMetadata() != null) {
            email = subscription.getMetadata().get("customer_email");
        }

        log.debug("extractEmailFromSubscription() | return={}", email);
        return email;
    }
}

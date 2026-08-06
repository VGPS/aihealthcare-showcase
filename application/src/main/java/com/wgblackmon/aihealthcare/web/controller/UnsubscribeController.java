package com.wgblackmon.aihealthcare.web.controller;

import com.stripe.model.Subscription;
import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Handles one-click unsubscribe links embedded in newsletter emails.
 *
 * <p>{@code GET /unsubscribe?token={uuid}} looks up the subscriber by their
 * unique unsubscribe token, deactivates them, and renders a confirmation page.
 * If the token is missing or invalid, an error message is shown instead.
 *
 * <p>This endpoint is publicly accessible (no authentication required) so
 * recipients can unsubscribe without logging in.
 *
 * <p>On unsubscribe, three events fire:
 * <ol>
 *   <li>Subscriber record deactivated ({@code active=false}, tier reverted to FREE, Stripe IDs cleared)</li>
 *   <li>Stripe subscription cancelled (if one exists)</li>
 *   <li>AppUser tier downgraded to FREE (if a login account exists)</li>
 * </ol>
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-07-31
 * @updated 2026-08-06
 */
@Slf4j
@Controller
public class UnsubscribeController {

    private final SubscriberPort subscriberPort;
    private final AppUserPort appUserPort;

    public UnsubscribeController(SubscriberPort subscriberPort, AppUserPort appUserPort) {
        log.debug("UnsubscribeController() | subscriberPort={}, appUserPort={}",
                subscriberPort.getClass().getSimpleName(), appUserPort.getClass().getSimpleName());
        this.subscriberPort = subscriberPort;
        this.appUserPort = appUserPort;
    }

    /**
     * Processes an unsubscribe request via token lookup.
     *
     * @param token the unique unsubscribe token from the email link.
     * @param model Thymeleaf model.
     * @return the "unsubscribe" view name.
     */
    @GetMapping("/unsubscribe")
    public String unsubscribe(@RequestParam(value = "token", required = false) String token,
                              Model model) {
        log.debug("unsubscribe() | token={}", token);

        if (token == null || token.isBlank()) {
            model.addAttribute("success", false);
            model.addAttribute("message", "Invalid unsubscribe link.");
            log.warn("unsubscribe() | Missing or blank token");
            log.debug("unsubscribe() | return=unsubscribe (invalid token)");
            return "unsubscribe";
        }

        Optional<Subscriber> subscriberOpt = subscriberPort.findByUnsubscribeToken(token);
        if (subscriberOpt.isEmpty()) {
            model.addAttribute("success", false);
            model.addAttribute("message", "This unsubscribe link is invalid or has already been used.");
            log.warn("unsubscribe() | No subscriber found for token={}", token);
            log.debug("unsubscribe() | return=unsubscribe (not found)");
            return "unsubscribe";
        }

        Subscriber sub = subscriberOpt.get();

        // 1. Cancel Stripe subscription if one exists
        cancelStripeSubscription(sub);

        // 2. Deactivate subscriber record — revert to FREE, clear Stripe IDs
        Subscriber deactivated = new Subscriber(
                sub.email(), sub.name(), false, sub.subscribedAt(), SubscriptionTier.FREE,
                sub.unsubscribeToken(), null, null);
        subscriberPort.save(deactivated);
        log.info("unsubscribe() | Subscriber deactivated: email={}", sub.email());

        // 3. Downgrade AppUser tier to FREE if a login account exists
        downgradeAppUser(sub.email());

        model.addAttribute("success", true);
        model.addAttribute("message", "You have been successfully unsubscribed.");
        model.addAttribute("email", sub.email());
        model.addAttribute("token", token);
        model.addAttribute("downgraded", false);

        log.debug("unsubscribe() | return=unsubscribe");
        return "unsubscribe";
    }

    /**
     * Downgrades an unsubscribed user to the free weekly digest.
     * Re-activates the subscriber record with FREE tier so they
     * receive the digest email but nothing else.
     */
    @PostMapping("/unsubscribe/downgrade")
    public String downgradeToDigest(@RequestParam(value = "token", required = false) String token,
                                     Model model) {
        log.debug("downgradeToDigest() | token={}", token);

        if (token == null || token.isBlank()) {
            model.addAttribute("success", false);
            model.addAttribute("message", "Invalid request.");
            log.debug("downgradeToDigest() | return=unsubscribe (missing token)");
            return "unsubscribe";
        }

        Optional<Subscriber> subscriberOpt = subscriberPort.findByUnsubscribeToken(token);
        if (subscriberOpt.isEmpty()) {
            model.addAttribute("success", false);
            model.addAttribute("message", "This link is no longer valid.");
            log.debug("downgradeToDigest() | return=unsubscribe (not found)");
            return "unsubscribe";
        }

        Subscriber sub = subscriberOpt.get();

        Subscriber reactivated = new Subscriber(
                sub.email(), sub.name(), true, sub.subscribedAt(), SubscriptionTier.FREE,
                sub.unsubscribeToken(), null, null);
        subscriberPort.save(reactivated);
        log.info("downgradeToDigest() | Re-activated as FREE digest: email={}", sub.email());

        model.addAttribute("success", true);
        model.addAttribute("message", "You've been switched to the free weekly digest.");
        model.addAttribute("email", sub.email());
        model.addAttribute("token", token);
        model.addAttribute("downgraded", true);

        log.debug("downgradeToDigest() | return=unsubscribe");
        return "unsubscribe";
    }

    private void cancelStripeSubscription(Subscriber sub) {
        log.debug("cancelStripeSubscription() | email={}, stripeSubscriptionId={}",
                sub.email(), sub.stripeSubscriptionId());

        if (sub.stripeSubscriptionId() == null || sub.stripeSubscriptionId().isBlank()) {
            log.debug("cancelStripeSubscription() | No Stripe subscription to cancel");
            log.debug("cancelStripeSubscription() | return=void");
            return;
        }

        try {
            Subscription subscription = Subscription.retrieve(sub.stripeSubscriptionId());
            subscription.cancel();
            log.info("cancelStripeSubscription() | Cancelled Stripe subscription: {}",
                    sub.stripeSubscriptionId());
        } catch (Exception e) {
            log.warn("cancelStripeSubscription() | Failed to cancel Stripe subscription {}: {}",
                    sub.stripeSubscriptionId(), e.getMessage());
        }

        log.debug("cancelStripeSubscription() | return=void");
    }

    private void downgradeAppUser(String email) {
        log.debug("downgradeAppUser() | email={}", email);

        Optional<AppUser> userOpt = appUserPort.findByEmail(email);
        if (userOpt.isEmpty()) {
            log.debug("downgradeAppUser() | No AppUser account found for email={}", email);
            log.debug("downgradeAppUser() | return=void");
            return;
        }

        AppUser user = userOpt.get();
        if (user.tier() == SubscriptionTier.FREE) {
            log.debug("downgradeAppUser() | Already FREE tier, no change needed");
            log.debug("downgradeAppUser() | return=void");
            return;
        }

        AppUser downgraded = new AppUser(
                user.email(), user.passwordHash(), user.displayName(),
                user.role(), user.enabled(), SubscriptionTier.FREE, null);
        appUserPort.save(downgraded);
        log.info("downgradeAppUser() | Downgraded to FREE: email={}, previousTier={}", email, user.tier());

        log.debug("downgradeAppUser() | return=void");
    }
}

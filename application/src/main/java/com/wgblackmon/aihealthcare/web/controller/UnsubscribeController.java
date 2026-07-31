package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-31
 * @updated 2026-07-31
 */
@Slf4j
@Controller
public class UnsubscribeController {

    private final SubscriberPort subscriberPort;

    public UnsubscribeController(SubscriberPort subscriberPort) {
        log.debug("UnsubscribeController() | subscriberPort={}", subscriberPort.getClass().getSimpleName());
        this.subscriberPort = subscriberPort;
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
        Subscriber deactivated = new Subscriber(
                sub.email(), sub.name(), false, sub.subscribedAt(), sub.tier(),
                sub.unsubscribeToken(), sub.stripeCustomerId(), sub.stripeSubscriptionId());
        subscriberPort.save(deactivated);

        model.addAttribute("success", true);
        model.addAttribute("message", "You have been successfully unsubscribed.");
        model.addAttribute("email", sub.email());

        log.info("unsubscribe() | Unsubscribed: email={}", sub.email());
        log.debug("unsubscribe() | return=unsubscribe");
        return "unsubscribe";
    }
}

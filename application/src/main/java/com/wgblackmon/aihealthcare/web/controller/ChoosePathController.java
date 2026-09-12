package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.Optional;

/**
 * Controller for the post-demo-expiration "choose your path" screen.
 *
 * <p>FREE_PENDING users are redirected here by the {@code DemoExpirationFilter}.
 * They can choose between:
 * <ul>
 *   <li><b>FREE</b> — disables app login, keeps subscriber active for email delivery</li>
 *   <li><b>SUBSCRIBER</b> — redirects to Stripe checkout for $19/month payment</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-20
 * @updated 2026-09-11
 */
@Slf4j
@Controller
public class ChoosePathController {

    private final AppUserPort appUserPort;
    private final SubscriberPort subscriberPort;
    private final StripeProperties stripeProperties;

    public ChoosePathController(AppUserPort appUserPort,
                                SubscriberPort subscriberPort,
                                StripeProperties stripeProperties) {
        log.debug("ChoosePathController() | appUserPort={}, subscriberPort={}, stripeProperties={}",
                  appUserPort.getClass().getSimpleName(),
                  subscriberPort.getClass().getSimpleName(),
                  stripeProperties.getClass().getSimpleName());
        this.appUserPort = appUserPort;
        this.subscriberPort = subscriberPort;
        this.stripeProperties = stripeProperties;
    }

    /**
     * Renders the "choose your path" decision page for FREE_PENDING users.
     *
     * @param model Thymeleaf model.
     * @return Thymeleaf view name "choose-path".
     */
    @GetMapping("/choose-path")
    public String showChoosePath(Model model) {
        log.debug("showChoosePath() | (no args)");

        model.addAttribute("stripeEnabled", stripeProperties.isEnabled());
        model.addAttribute("subscriberPriceId", stripeProperties.getSubscriberPriceId());

        log.debug("showChoosePath() | return=choose-path");
        return "choose-path";
    }

    /**
     * Processes the user's path choice.
     *
     * @param choice    "free" or "subscriber".
     * @param principal the authenticated user.
     * @return redirect to login (FREE) or pricing (SUBSCRIBER).
     */
    @Transactional
    @PostMapping("/choose-path")
    public String choosePath(@RequestParam String choice, Principal principal) {
        log.debug("choosePath() | choice={}, principal={}", choice, principal.getName());

        String email = principal.getName();

        if ("free".equals(choice)) {
            // Disable app user, set tier to FREE
            Optional<AppUser> userOpt = appUserPort.findByEmail(email);
            if (userOpt.isPresent()) {
                AppUser user = userOpt.get();
                AppUser updated = new AppUser(user.email(), user.passwordHash(), user.displayName(),
                        user.role(), false, SubscriptionTier.FREE, user.demoExpiresAt());
                appUserPort.save(updated);
            }

            // Set subscriber tier to FREE (keep active for email delivery)
            Optional<Subscriber> subOpt = subscriberPort.findByEmail(email);
            if (subOpt.isPresent()) {
                Subscriber sub = subOpt.get();
                Subscriber updated = new Subscriber(sub.email(), sub.name(), true,
                        sub.subscribedAt(), SubscriptionTier.FREE,
                        sub.unsubscribeToken(), sub.stripeCustomerId(), sub.stripeSubscriptionId());
                subscriberPort.save(updated);
            }

            log.debug("choosePath() | return=redirect:/login?demo-expired (FREE path)");
            return "redirect:/login?demo-expired";
        } else {
            log.debug("choosePath() | return=redirect:/pricing (SUBSCRIBER path)");
            return "redirect:/pricing";
        }
    }
}

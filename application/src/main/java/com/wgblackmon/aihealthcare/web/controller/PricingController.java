package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import com.wgblackmon.aihealthcare.infrastructure.config.TierLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf controller that renders the public pricing page at {@code GET /pricing}.
 *
 * <p>Displays the two-tier comparison (Free vs Member) with feature limits loaded
 * from {@link TierLimitProperties} and an upgrade button backed by the Stripe
 * publishable key from {@link StripeProperties}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-26
 * @updated 2026-05-26
 */
@Slf4j
@Controller
public class PricingController {

    private final TierLimitProperties tierLimitProperties;
    private final StripeProperties    stripeProperties;

    public PricingController(TierLimitProperties tierLimitProperties,
                             StripeProperties stripeProperties) {
        log.debug("PricingController() | tierLimitProperties={}, stripeProperties={}",
                  tierLimitProperties.getClass().getSimpleName(),
                  stripeProperties.getClass().getSimpleName());
        this.tierLimitProperties = tierLimitProperties;
        this.stripeProperties    = stripeProperties;
    }

    /**
     * Renders the pricing comparison page.
     *
     * @param model Thymeleaf model populated with tier details and Stripe config.
     * @return Thymeleaf view name "pricing".
     */
    @GetMapping("/pricing")
    public String pricing(Model model) {
        log.debug("pricing() | (no args)");

        TierLimitProperties.TierConfig free   = tierLimitProperties.getFree();
        TierLimitProperties.TierConfig member = tierLimitProperties.getMember();

        model.addAttribute("freeArchiveDays", free.getArchiveDays());
        model.addAttribute("freeQueryLimit", free.getMonthlyQueryLimit());
        model.addAttribute("memberArchiveDays", member.getArchiveDays());
        model.addAttribute("memberQueryLimit", member.getMonthlyQueryLimit());
        model.addAttribute("stripeEnabled", stripeProperties.isEnabled());
        model.addAttribute("stripePublishableKey", stripeProperties.getPublishableKey());
        model.addAttribute("memberPriceId", stripeProperties.getMemberPriceId());

        log.debug("pricing() | return=pricing");
        return "pricing";
    }
}

package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.infrastructure.config.StripeProperties;
import com.wgblackmon.aihealthcare.infrastructure.config.TierLimitProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Thymeleaf controller that renders the subscriber self-service profile page
 * at {@code GET /profile}.
 *
 * <p>Displays the current user's subscription tier, usage statistics for the
 * current month, and a link to the Stripe Customer Portal for managing
 * their subscription.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-03
 * @updated 2026-07-03
 */
@Slf4j
@Controller
public class ProfileController {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final SubscriberPort subscriberPort;
    private final UsageTrackingPort usageTrackingPort;
    private final TierLimitProperties tierLimitProperties;
    private final StripeProperties stripeProperties;

    public ProfileController(SubscriberPort subscriberPort,
                             UsageTrackingPort usageTrackingPort,
                             TierLimitProperties tierLimitProperties,
                             StripeProperties stripeProperties) {
        log.debug("ProfileController() | subscriberPort={}, usageTrackingPort={}",
                  subscriberPort.getClass().getSimpleName(),
                  usageTrackingPort.getClass().getSimpleName());
        this.subscriberPort = subscriberPort;
        this.usageTrackingPort = usageTrackingPort;
        this.tierLimitProperties = tierLimitProperties;
        this.stripeProperties = stripeProperties;
    }

    /**
     * Renders the profile page with tier info, usage stats, and Stripe portal link.
     *
     * @param model     Thymeleaf model.
     * @param principal the authenticated user.
     * @return Thymeleaf view name "profile".
     */
    @GetMapping("/profile")
    public String profile(Model model, Principal principal) {
        log.debug("profile() | principal={}", principal != null ? principal.getName() : "anonymous");

        String email = principal != null ? principal.getName() : "";
        model.addAttribute("email", email);

        // Resolve subscription tier
        Optional<Subscriber> subscriberOpt = subscriberPort.findByEmail(email);
        SubscriptionTier tier = subscriberOpt
                .map(Subscriber::tier)
                .orElse(SubscriptionTier.FREE);

        boolean isAdmin = SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
        if (isAdmin) {
            tier = SubscriptionTier.MEMBER;
        }

        model.addAttribute("tier", tier.name());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("subscriberName", subscriberOpt.map(Subscriber::name).orElse(email));
        model.addAttribute("subscribedAt", subscriberOpt
                .map(s -> DateTimeFormatter.ISO_LOCAL_DATE.format(
                        s.subscribedAt().atZone(ZoneOffset.UTC)))
                .orElse("N/A"));

        // Usage stats for current month
        String yearMonth = MONTH_FMT.format(Instant.now().atZone(ZoneOffset.UTC));
        UsageRecord usage = usageTrackingPort.getOrCreateUsage(email, yearMonth);
        model.addAttribute("queriesUsed", usage.queryCount());
        model.addAttribute("queryLimit", usage.queryLimit());
        int pct = usage.queryLimit() > 0
                ? (int) ((usage.queryCount() * 100.0) / usage.queryLimit())
                : 0;
        model.addAttribute("usagePct", Math.min(pct, 100));

        // Tier limits for display
        TierLimitProperties.TierConfig limits = tier == SubscriptionTier.MEMBER
                ? tierLimitProperties.getMember()
                : tierLimitProperties.getFree();
        model.addAttribute("archiveDays", limits.getArchiveDays());

        // Stripe config
        model.addAttribute("stripeEnabled", stripeProperties.isEnabled());
        model.addAttribute("memberPriceId", stripeProperties.getMemberPriceId());

        log.debug("profile() | return=profile");
        return "profile";
    }
}

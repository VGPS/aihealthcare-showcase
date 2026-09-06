package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ApiKey;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.UsageRecord;
import com.wgblackmon.aihealthcare.domain.port.outbound.ApiKeyPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.UsageTrackingPort;
import com.wgblackmon.aihealthcare.web.dto.ApiKeyResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Thymeleaf controller for the Developer Portal page at {@code GET /developer}.
 *
 * <p>Displays API key management, usage statistics, rate limit information,
 * and code examples for integrating with the AIHealthcare REST API. Swagger
 * UI is linked for interactive API exploration.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-09-06
 */
@Slf4j
@Controller
public class DeveloperPortalController {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ApiKeyPort apiKeyPort;
    private final UsageTrackingPort usageTrackingPort;
    private final SubscriberPort subscriberPort;

    public DeveloperPortalController(ApiKeyPort apiKeyPort,
                                     UsageTrackingPort usageTrackingPort,
                                     SubscriberPort subscriberPort) {
        log.debug("DeveloperPortalController() | apiKeyPort={}, usageTrackingPort={}, subscriberPort={}",
                  apiKeyPort.getClass().getSimpleName(),
                  usageTrackingPort.getClass().getSimpleName(),
                  subscriberPort.getClass().getSimpleName());
        this.apiKeyPort = apiKeyPort;
        this.usageTrackingPort = usageTrackingPort;
        this.subscriberPort = subscriberPort;
    }

    /**
     * Renders the developer portal page.
     *
     * <p>Authenticated users see their API keys, usage stats, and key
     * management controls. Unauthenticated visitors see documentation,
     * code examples, and a login prompt.
     *
     * @param model     Thymeleaf model.
     * @param principal the authenticated user (may be null).
     * @return Thymeleaf view name "developer".
     */
    @GetMapping("/developer")
    public String developer(Model model, Principal principal) {
        log.debug("developer() | principal={}", principal != null ? principal.getName() : "null");

        boolean authenticated = principal != null;
        model.addAttribute("authenticated", authenticated);
        model.addAttribute("activePage", "developer");

        if (authenticated) {
            String email = principal.getName();
            model.addAttribute("email", email);

            SubscriptionTier tier = resolveTier(principal);
            boolean isAdmin = isAdmin(principal);
            model.addAttribute("tier", tier.name());
            model.addAttribute("isAdmin", isAdmin);

            boolean canCreateKeys = isAdmin
                    || tier == SubscriptionTier.SUBSCRIBER
                    || tier == SubscriptionTier.ENTERPRISE;
            model.addAttribute("canCreateKeys", canCreateKeys);

            int maxKeys;
            if (isAdmin) {
                maxKeys = 100;
            } else if (tier == SubscriptionTier.ENTERPRISE) {
                maxKeys = 10;
            } else {
                maxKeys = 3;
            }
            model.addAttribute("maxKeys", maxKeys);

            List<ApiKey> keys = apiKeyPort.findAllByOwnerEmail(email);
            List<ApiKeyResponse> keyResponses = new ArrayList<>();
            for (ApiKey key : keys) {
                keyResponses.add(new ApiKeyResponse(key.id(), key.name(), key.keyPrefix(),
                        null, key.active(), key.createdAt()));
            }
            model.addAttribute("keys", keyResponses);
            model.addAttribute("keyCount", keys.size());

            String yearMonth = MONTH_FMT.format(Instant.now().atZone(ZoneOffset.UTC));
            UsageRecord usage = usageTrackingPort.getOrCreateUsage(email, yearMonth);
            model.addAttribute("queriesUsed", usage.queryCount());
            model.addAttribute("queryLimit", usage.queryLimit());
            int pct = usage.queryLimit() > 0
                    ? (int) ((usage.queryCount() * 100.0) / usage.queryLimit())
                    : 0;
            model.addAttribute("usagePct", Math.min(pct, 100));
        }

        model.addAttribute("rateLimitPerMinute", 60);

        log.debug("developer() | return=developer");
        return "developer";
    }

    private SubscriptionTier resolveTier(Principal principal) {
        if (principal == null) return SubscriptionTier.FREE;
        if (isAdmin(principal)) return SubscriptionTier.SUBSCRIBER;
        return subscriberPort.findByEmail(principal.getName())
                .map(Subscriber::tier).orElse(SubscriptionTier.FREE);
    }

    private boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication auth) {
            return auth.getAuthorities().stream()
                    .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        }
        return false;
    }
}

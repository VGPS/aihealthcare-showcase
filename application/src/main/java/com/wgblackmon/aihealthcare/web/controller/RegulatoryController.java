package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.RegulatoryBody;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller that renders the regulatory alerts dashboard page.
 *
 * <p>Serves {@code GET /dashboard/regulatory} by loading recent
 * {@link RegulatoryEvent} records and populating the Thymeleaf model
 * with events grouped and filtered by regulatory body and type.
 *
 * <p>Tier gating: SUBSCRIBER and DEMO users see all events;
 * FREE users see only the 5 most recent.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Slf4j
@Controller
public class RegulatoryController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.of("America/New_York"));

    private static final int FREE_EVENT_LIMIT = 5;
    private static final int FULL_EVENT_LIMIT = 50;

    private final MonitorRegulatoryEventsUseCase regulatoryUseCase;
    private final SubscriberPort subscriberPort;

    public RegulatoryController(MonitorRegulatoryEventsUseCase regulatoryUseCase,
                                SubscriberPort subscriberPort) {
        log.debug("RegulatoryController() | regulatoryUseCase={}, subscriberPort={}",
                  regulatoryUseCase, subscriberPort);
        this.regulatoryUseCase = regulatoryUseCase;
        this.subscriberPort = subscriberPort;
    }

    /**
     * Renders the regulatory alerts page.
     *
     * @param filter    optional filter: "fda", "cms", or null for all
     * @param principal the authenticated user, or null for anonymous
     * @param model     Thymeleaf model
     * @return the "regulatory" view name
     */
    @GetMapping("/dashboard/regulatory")
    public String regulatory(@RequestParam(required = false) String filter,
                              Principal principal,
                              Model model) {
        log.debug("regulatory() | filter={}, principal={}", filter,
                  principal != null ? principal.getName() : "anonymous");

        SubscriptionTier tier = resolveTier(principal);
        boolean fullAccess = tier == SubscriptionTier.SUBSCRIBER
                || tier == SubscriptionTier.DEMO
                || isAdmin(principal);
        int limit = fullAccess ? FULL_EVENT_LIMIT : FREE_EVENT_LIMIT;

        List<RegulatoryEvent> events;
        if ("fda".equalsIgnoreCase(filter)) {
            events = regulatoryUseCase.getEventsByBody(RegulatoryBody.FDA, limit);
        } else if ("cms".equalsIgnoreCase(filter)) {
            events = regulatoryUseCase.getEventsByBody(RegulatoryBody.CMS, limit);
        } else {
            events = regulatoryUseCase.getRecentEvents(limit);
        }

        // Format dates server-side
        Map<String, String> eventDates = new HashMap<>();
        Map<String, String> discoveredDates = new HashMap<>();
        for (RegulatoryEvent event : events) {
            if (event.publishedAt() != null) {
                eventDates.put(event.eventId(), DISPLAY_FMT.format(event.publishedAt()));
            }
            discoveredDates.put(event.eventId(), DISPLAY_FMT.format(event.discoveredAt()));
        }

        // Type counts for summary badges
        int fda510kCount = 0;
        int deNovoCount = 0;
        int cmsCount = 0;
        int otherCount = 0;
        for (RegulatoryEvent event : events) {
            if (event.eventType() == RegulatoryEventType.FDA_510K_CLEARANCE) {
                fda510kCount++;
            } else if (event.eventType() == RegulatoryEventType.FDA_DE_NOVO_CLASSIFICATION) {
                deNovoCount++;
            } else if (event.eventType() == RegulatoryEventType.CMS_PROPOSED_RULE
                    || event.eventType() == RegulatoryEventType.CMS_FINAL_RULE
                    || event.eventType() == RegulatoryEventType.CMS_NCD) {
                cmsCount++;
            } else {
                otherCount++;
            }
        }

        model.addAttribute("events", events);
        model.addAttribute("eventDates", eventDates);
        model.addAttribute("discoveredDates", discoveredDates);
        model.addAttribute("eventCount", events.size());
        model.addAttribute("fda510kCount", fda510kCount);
        model.addAttribute("deNovoCount", deNovoCount);
        model.addAttribute("cmsCount", cmsCount);
        model.addAttribute("otherCount", otherCount);
        model.addAttribute("filter", filter);
        model.addAttribute("fullAccess", fullAccess);

        log.debug("regulatory() | return=regulatory, eventCount={}", events.size());
        return "regulatory";
    }

    private SubscriptionTier resolveTier(Principal principal) {
        log.debug("resolveTier() | principal={}", principal != null ? principal.getName() : "null");
        if (principal == null) {
            log.debug("resolveTier() | return={}", SubscriptionTier.FREE);
            return SubscriptionTier.FREE;
        }
        if (isAdmin(principal)) {
            log.debug("resolveTier() | ADMIN role detected, return={}", SubscriptionTier.SUBSCRIBER);
            return SubscriptionTier.SUBSCRIBER;
        }
        Optional<Subscriber> subscriber = subscriberPort.findByEmail(principal.getName());
        SubscriptionTier result = subscriber.map(Subscriber::tier).orElse(SubscriptionTier.FREE);
        log.debug("resolveTier() | return={}", result);
        return result;
    }

    private boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication auth) {
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        return false;
    }
}

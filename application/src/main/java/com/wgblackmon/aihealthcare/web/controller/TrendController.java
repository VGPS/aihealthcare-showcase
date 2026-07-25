package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Thymeleaf controller that renders the trend detection dashboard page.
 *
 * <p>Serves {@code GET /dashboard/trends} by loading the latest
 * {@link TrendSnapshot} and populating the Thymeleaf model with rising,
 * fading, and newly emerged keyword signals.
 *
 * <p>Tier gating: SUBSCRIBER and DEMO users see the full trend lists;
 * FREE users see only the top 5 rising topics.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-07-22
 * @updated 2026-07-24
 */
@Slf4j
@Controller
public class TrendController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z")
                    .withZone(ZoneId.of("America/New_York"));

    private static final int FREE_RISING_LIMIT = 5;

    private static final String SCORING_RUBRIC =
            "1-3: Noise (passing mention, opinion) | " +
            "4-6: Routine (market reports, incremental updates) | " +
            "7-8: Significant (product launch, clinical result, FDA action) | " +
            "9-10: Landmark (first-of-kind, paradigm shift, breakthrough)";

    private final DetectTrendsUseCase detectTrendsUseCase;
    private final SubscriberPort subscriberPort;

    public TrendController(DetectTrendsUseCase detectTrendsUseCase,
                           SubscriberPort subscriberPort) {
        log.debug("TrendController() | detectTrendsUseCase={}, subscriberPort={}",
                  detectTrendsUseCase, subscriberPort);
        this.detectTrendsUseCase = detectTrendsUseCase;
        this.subscriberPort = subscriberPort;
    }

    /**
     * Renders the trend detection dashboard page.
     *
     * @param principal the authenticated user, or null for anonymous
     * @param model     Thymeleaf model
     * @return the "trends" view name
     */
    @GetMapping("/dashboard/trends")
    public String trends(Principal principal, Model model) {
        log.debug("trends() | principal={}", principal != null ? principal.getName() : "anonymous");

        Optional<TrendSnapshot> latest = detectTrendsUseCase.getLatestSnapshot();

        if (latest.isPresent()) {
            TrendSnapshot snapshot = latest.get();
            SubscriptionTier tier = resolveTier(principal);
            boolean fullAccess = tier == SubscriptionTier.SUBSCRIBER || tier == SubscriptionTier.DEMO;

            List<TrendSignal> risingTopics;
            if (fullAccess || isAdmin(principal)) {
                risingTopics = snapshot.risingTopics();
            } else {
                risingTopics = limitList(snapshot.risingTopics(), FREE_RISING_LIMIT);
            }

            List<TrendSignal> fadingTopics = fullAccess || isAdmin(principal)
                    ? snapshot.fadingTopics() : List.of();
            List<TrendSignal> newTopics = fullAccess || isAdmin(principal)
                    ? snapshot.newTopics() : List.of();

            model.addAttribute("risingTopics", risingTopics);
            model.addAttribute("fadingTopics", fadingTopics);
            model.addAttribute("newTopics", newTopics);
            model.addAttribute("totalKeywords", snapshot.totalKeywords());
            model.addAttribute("generatedAt", DISPLAY_FMT.format(snapshot.generatedAt()));
            model.addAttribute("hasSnapshot", true);
            model.addAttribute("fullAccess", fullAccess || isAdmin(principal));

            // Chart data: top 10 rising keywords + counts for bar chart
            List<String> chartLabels = new ArrayList<>();
            List<Long> chartData = new ArrayList<>();
            int chartLimit = Math.min(risingTopics.size(), 10);
            for (int i = 0; i < chartLimit; i++) {
                TrendSignal signal = risingTopics.get(i);
                chartLabels.add(signal.keyword());
                chartData.add(signal.current30d());
            }
            model.addAttribute("chartLabels", chartLabels);
            model.addAttribute("chartData", chartData);
            model.addAttribute("scoringRubric", SCORING_RUBRIC);
        } else {
            model.addAttribute("hasSnapshot", false);
            model.addAttribute("risingTopics", List.of());
            model.addAttribute("fadingTopics", List.of());
            model.addAttribute("newTopics", List.of());
            model.addAttribute("totalKeywords", 0);
            model.addAttribute("generatedAt", "N/A");
            model.addAttribute("fullAccess", false);
            model.addAttribute("chartLabels", List.of());
            model.addAttribute("chartData", List.of());
            model.addAttribute("scoringRubric", SCORING_RUBRIC);
        }

        log.debug("trends() | return=trends");
        return "trends";
    }

    private List<TrendSignal> limitList(List<TrendSignal> signals, int limit) {
        if (signals.size() <= limit) {
            return signals;
        }
        return new ArrayList<>(signals.subList(0, limit));
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

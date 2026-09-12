package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.LegalTrendSignal;
import com.wgblackmon.aihealthcare.domain.model.LegalTrendSnapshot;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectLegalTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.infrastructure.scheduler.PipelineAsyncRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Thymeleaf controller that renders the legal trend analysis page.
 *
 * <p>Serves {@code GET /dashboard/legal/trends} by loading the latest
 * {@link LegalTrendSnapshot} and populating the Thymeleaf model with
 * rising legal/regulatory trend signals.
 *
 * <p>Tier gating: FREE users see only the top 3 trends;
 * SUBSCRIBER, DEMO, and ADMIN users see all.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-09-11
 */
@Slf4j
@Controller
public class LegalTrendController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z")
                    .withZone(ZoneId.of("America/New_York"));

    private static final int FREE_TREND_LIMIT = 3;

    private final DetectLegalTrendsUseCase detectLegalTrendsUseCase;
    private final SubscriberPort subscriberPort;
    private final ArticleIngestionPort articleIngestionPort;
    private final MonitorRegulatoryEventsUseCase regulatoryUseCase;
    private final PipelineAsyncRunner asyncRunner;
    private final TierResolver tierResolver;

    public LegalTrendController(DetectLegalTrendsUseCase detectLegalTrendsUseCase,
                                SubscriberPort subscriberPort,
                                ArticleIngestionPort articleIngestionPort,
                                MonitorRegulatoryEventsUseCase regulatoryUseCase,
                                PipelineAsyncRunner asyncRunner,
                                TierResolver tierResolver) {
        log.debug("LegalTrendController() | detectLegalTrendsUseCase={}, subscriberPort={}, " +
                  "articleIngestionPort={}, regulatoryUseCase={}, asyncRunner={}, tierResolver={}",
                  detectLegalTrendsUseCase, subscriberPort, articleIngestionPort, regulatoryUseCase, asyncRunner, tierResolver);
        this.detectLegalTrendsUseCase = detectLegalTrendsUseCase;
        this.subscriberPort = subscriberPort;
        this.articleIngestionPort = articleIngestionPort;
        this.regulatoryUseCase = regulatoryUseCase;
        this.asyncRunner = asyncRunner;
        this.tierResolver = tierResolver;
    }

    /**
     * Unified display item for linked articles and regulatory events in trend cards.
     */
    public record LinkedItem(String title, String url, String source, String date) {
    }

    /**
     * Renders the legal trend analysis page.
     *
     * @param principal the authenticated user, or null for anonymous
     * @param model     Thymeleaf model
     * @return the "legal-trends" view name
     */
    @GetMapping("/dashboard/legal/trends")
    public String legalTrends(Principal principal, Model model) {
        log.debug("legalTrends() | principal={}", principal != null ? principal.getName() : "anonymous");

        Optional<LegalTrendSnapshot> latest = detectLegalTrendsUseCase.getLatestSnapshot();

        if (latest.isPresent()) {
            LegalTrendSnapshot snapshot = latest.get();
            boolean fullAccess = hasFullAccess(principal);

            List<LegalTrendSignal> trends;
            if (fullAccess) {
                trends = snapshot.risingTrends();
            } else {
                trends = limitList(snapshot.risingTrends(), FREE_TREND_LIMIT);
            }

            // Title-case keywords for display
            List<LegalTrendSignal> displayTrends = new ArrayList<>();
            for (LegalTrendSignal signal : trends) {
                displayTrends.add(new LegalTrendSignal(
                        toTitleCase(signal.keyword()), signal.category(),
                        signal.current30d(), signal.previous90d(),
                        signal.momentum(), signal.direction(),
                        signal.topArticleIds(), signal.summary()));
            }

            // Count by category
            int litigationCount = 0;
            int regulationCount = 0;
            int policyCount = 0;
            for (LegalTrendSignal signal : snapshot.risingTrends()) {
                if ("LITIGATION".equals(signal.category())) {
                    litigationCount++;
                } else if ("REGULATION".equals(signal.category())) {
                    regulationCount++;
                } else if ("POLICY".equals(signal.category())) {
                    policyCount++;
                }
            }

            // Resolve linked articles + regulatory events for each trend
            Map<String, List<LinkedItem>> trendLinkedItems = new HashMap<>();
            for (LegalTrendSignal signal : displayTrends) {
                if (!signal.topArticleIds().isEmpty()) {
                    List<LinkedItem> items = new ArrayList<>();

                    // Resolve articles
                    List<NewsArticle> articles = articleIngestionPort.fetchArticlesByIds(signal.topArticleIds());
                    Set<String> resolvedIds = new HashSet<>();
                    for (NewsArticle article : articles) {
                        resolvedIds.add(article.articleId());
                        String date = article.publishedAt() != null
                                ? DISPLAY_FMT.format(article.publishedAt()) : null;
                        items.add(new LinkedItem(
                                article.title(),
                                article.url() != null ? article.url().toString() : null,
                                article.sourceName(),
                                date));
                    }

                    // Resolve unmatched IDs as regulatory events
                    for (String id : signal.topArticleIds()) {
                        if (!resolvedIds.contains(id)) {
                            Optional<RegulatoryEvent> event = regulatoryUseCase.getEvent(id);
                            if (event.isPresent()) {
                                RegulatoryEvent e = event.get();
                                String date = e.discoveredAt() != null
                                        ? DISPLAY_FMT.format(e.discoveredAt()) : null;
                                String source = e.regulatoryBody().name()
                                        + (e.applicantName() != null ? " — " + e.applicantName() : "");
                                items.add(new LinkedItem(
                                        e.title(),
                                        e.sourceUrl(),
                                        source,
                                        date));
                            }
                        }
                    }

                    trendLinkedItems.put(signal.keyword(), items);
                }
            }
            model.addAttribute("trendLinkedItems", trendLinkedItems);

            model.addAttribute("risingTrends", displayTrends);
            model.addAttribute("totalKeywords", snapshot.totalKeywords());
            model.addAttribute("generatedAt", DISPLAY_FMT.format(snapshot.generatedAt()));
            model.addAttribute("hasSnapshot", true);
            model.addAttribute("fullAccess", fullAccess);
            model.addAttribute("litigationCount", litigationCount);
            model.addAttribute("regulationCount", regulationCount);
            model.addAttribute("policyCount", policyCount);

            // Chart data
            List<String> chartLabels = new ArrayList<>();
            List<Long> chartData = new ArrayList<>();
            int chartLimit = Math.min(displayTrends.size(), 10);
            for (int i = 0; i < chartLimit; i++) {
                LegalTrendSignal signal = displayTrends.get(i);
                chartLabels.add(signal.keyword());
                chartData.add(signal.current30d());
            }
            model.addAttribute("chartLabels", chartLabels);
            model.addAttribute("chartData", chartData);
        } else {
            model.addAttribute("hasSnapshot", false);
            model.addAttribute("risingTrends", List.of());
            model.addAttribute("totalKeywords", 0);
            model.addAttribute("generatedAt", "N/A");
            model.addAttribute("fullAccess", false);
            model.addAttribute("litigationCount", 0);
            model.addAttribute("regulationCount", 0);
            model.addAttribute("policyCount", 0);
            model.addAttribute("chartLabels", List.of());
            model.addAttribute("chartData", List.of());
            model.addAttribute("trendLinkedItems", Map.of());
        }

        log.debug("legalTrends() | return=legal-trends");
        return "legal-trends";
    }

    /**
     * Triggers legal trend detection (ADMIN only). Redirects back to the page.
     */
    @PostMapping("/dashboard/legal/trends/detect")
    public String triggerDetection(Principal principal) {
        log.debug("triggerDetection() | principal={}", principal != null ? principal.getName() : "anonymous");

        if (!tierResolver.isAdmin(principal)) {
            log.warn("triggerDetection() | non-admin attempted detection, redirecting");
            return "redirect:/dashboard/legal/trends";
        }

        asyncRunner.runAsync("legal-trends", () -> detectLegalTrendsUseCase.detectLegalTrends());
        return "redirect:/dashboard/legal/trends";
    }

    private List<LegalTrendSignal> limitList(List<LegalTrendSignal> signals, int limit) {
        if (signals.size() <= limit) {
            return signals;
        }
        return new ArrayList<>(signals.subList(0, limit));
    }

    private boolean hasFullAccess(Principal principal) {
        if (principal == null) {
            return false;
        }
        if (tierResolver.isAdmin(principal)) {
            return true;
        }
        Optional<Subscriber> subscriber = subscriberPort.findByEmail(principal.getName());
        if (subscriber.isPresent()) {
            SubscriptionTier tier = subscriber.get().tier();
            return tier == SubscriptionTier.SUBSCRIBER || tier == SubscriptionTier.DEMO
                    || tier == SubscriptionTier.ENTERPRISE;
        }
        return false;
    }

    private String toTitleCase(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return keyword;
        }
        String[] words = keyword.split(" ");
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                result.append(' ');
            }
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1));
                }
            }
        }
        return result.toString();
    }
}

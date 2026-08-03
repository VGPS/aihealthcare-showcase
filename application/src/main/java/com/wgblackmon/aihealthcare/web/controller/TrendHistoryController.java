package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.model.TrendDirection;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import com.wgblackmon.aihealthcare.web.dto.TrendSnapshotSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller for the historical trend archive pages.
 *
 * <p>Serves two views:
 * <ul>
 *   <li>{@code GET /dashboard/trends/history} — overview with a multi-line
 *       Chart.js chart tracking top keywords across snapshots, plus a
 *       clickable timeline table of all past snapshots.</li>
 *   <li>{@code GET /dashboard/trends/history/{epochMillis}} — detail view
 *       for a single past snapshot showing rising/new keywords with their
 *       articles and a bar chart.</li>
 * </ul>
 *
 * <p>Tier gating: FREE users see the last 4 snapshots; SUBSCRIBER, DEMO,
 * and ADMIN users see the full history.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@Controller
public class TrendHistoryController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a z")
                    .withZone(ZoneId.of("America/New_York"));

    private static final DateTimeFormatter SHORT_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d")
                    .withZone(ZoneId.of("America/New_York"));

    private static final int FREE_SNAPSHOT_LIMIT = 4;
    private static final int TOP_KEYWORDS_LIMIT = 10;

    private final DetectTrendsUseCase detectTrendsUseCase;
    private final SubscriberPort subscriberPort;

    public TrendHistoryController(DetectTrendsUseCase detectTrendsUseCase,
                                  SubscriberPort subscriberPort) {
        log.debug("TrendHistoryController() | detectTrendsUseCase={}, subscriberPort={}",
                  detectTrendsUseCase, subscriberPort);
        this.detectTrendsUseCase = detectTrendsUseCase;
        this.subscriberPort = subscriberPort;
    }

    /**
     * Renders the trend history overview page with a multi-line chart and
     * snapshot timeline table.
     */
    @GetMapping("/dashboard/trends/history")
    public String history(Principal principal, Model model) {
        log.debug("history() | principal={}", principal != null ? principal.getName() : "anonymous");

        List<TrendSnapshot> allSnapshots = detectTrendsUseCase.getAllSnapshots();
        boolean fullAccess = hasFullAccess(principal);

        List<TrendSnapshot> gated;
        if (fullAccess || allSnapshots.size() <= FREE_SNAPSHOT_LIMIT) {
            gated = allSnapshots;
        } else {
            gated = new ArrayList<>(allSnapshots.subList(0, FREE_SNAPSHOT_LIMIT));
        }

        boolean hasHistory = !gated.isEmpty();
        model.addAttribute("hasHistory", hasHistory);
        model.addAttribute("fullAccess", fullAccess);
        model.addAttribute("snapshotCount", allSnapshots.size());

        if (hasHistory) {
            // Reverse to chronological order for chart
            List<TrendSnapshot> chronological = new ArrayList<>(gated);
            reverseList(chronological);

            // Build timeline table summaries (keep desc order)
            List<TrendSnapshotSummary> summaries = new ArrayList<>();
            for (TrendSnapshot snapshot : gated) {
                summaries.add(new TrendSnapshotSummary(
                        snapshot.generatedAt().toEpochMilli(),
                        DISPLAY_FMT.format(snapshot.generatedAt()),
                        snapshot.risingTopics().size(),
                        snapshot.newTopics().size(),
                        snapshot.totalKeywords(),
                        snapshot.windowDays()));
            }
            model.addAttribute("snapshots", summaries);

            // Build multi-line chart data
            buildChartData(chronological, model);
        }

        log.debug("history() | return=trend-history");
        return "trend-history";
    }

    /**
     * Renders the detail view for a single past trend snapshot, identified
     * by its {@code generatedAt} timestamp as epoch milliseconds.
     */
    @GetMapping("/dashboard/trends/history/{epochMillis}")
    public String snapshotDetail(@PathVariable long epochMillis,
                                 Principal principal, Model model) {
        log.debug("snapshotDetail() | epochMillis={}, principal={}",
                  epochMillis, principal != null ? principal.getName() : "anonymous");

        List<TrendSnapshot> allSnapshots = detectTrendsUseCase.getAllSnapshots();
        TrendSnapshot target = null;
        for (TrendSnapshot snapshot : allSnapshots) {
            if (snapshot.generatedAt().toEpochMilli() == epochMillis) {
                target = snapshot;
                break;
            }
        }

        if (target == null) {
            log.debug("snapshotDetail() | snapshot not found for epochMillis={}", epochMillis);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Snapshot not found");
        }

        // Title-case keywords for display
        List<TrendSignal> risingTopics = new ArrayList<>();
        for (TrendSignal signal : target.risingTopics()) {
            risingTopics.add(new TrendSignal(
                    toTitleCase(signal.keyword()),
                    signal.current30d(), signal.previous90d(),
                    signal.baseline180d(), signal.momentum(),
                    signal.direction(), signal.firstSeenAt(),
                    signal.topArticles(), signal.summary()));
        }

        List<TrendSignal> newTopics = new ArrayList<>();
        for (TrendSignal signal : target.newTopics()) {
            newTopics.add(new TrendSignal(
                    toTitleCase(signal.keyword()),
                    signal.current30d(), signal.previous90d(),
                    signal.baseline180d(), signal.momentum(),
                    signal.direction(), signal.firstSeenAt(),
                    signal.topArticles(), signal.summary()));
        }

        model.addAttribute("risingTopics", risingTopics);
        model.addAttribute("newTopics", newTopics);
        model.addAttribute("totalKeywords", target.totalKeywords());
        model.addAttribute("generatedAt", DISPLAY_FMT.format(target.generatedAt()));
        model.addAttribute("windowDays", target.windowDays());

        // Bar chart data for this snapshot's rising keywords
        List<String> chartLabels = new ArrayList<>();
        List<Long> chartData = new ArrayList<>();
        int chartLimit = Math.min(risingTopics.size(), TOP_KEYWORDS_LIMIT);
        for (int i = 0; i < chartLimit; i++) {
            TrendSignal signal = risingTopics.get(i);
            chartLabels.add(signal.keyword());
            chartData.add(signal.current30d());
        }
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartData", chartData);

        // Article dates map for display
        Map<String, String> articleDates = new HashMap<>();
        for (TrendSignal signal : risingTopics) {
            for (ScoredArticle scored : signal.topArticles()) {
                if (scored.publishedAt() != null) {
                    articleDates.put(scored.articleId(), DISPLAY_FMT.format(scored.publishedAt()));
                }
            }
        }
        model.addAttribute("articleDates", articleDates);

        log.debug("snapshotDetail() | return=trend-history-detail");
        return "trend-history-detail";
    }

    /**
     * Builds the multi-line chart data from chronologically-ordered snapshots.
     * Tracks the top 10 most-frequently-appearing keywords across all snapshots.
     */
    private void buildChartData(List<TrendSnapshot> chronological, Model model) {
        log.debug("buildChartData() | snapshotCount={}", chronological.size());

        // 1. Build x-axis labels (snapshot dates)
        List<String> historyDates = new ArrayList<>();
        for (TrendSnapshot snapshot : chronological) {
            historyDates.add(SHORT_DATE_FMT.format(snapshot.generatedAt()));
        }

        // 2. Count how many snapshots each keyword appears in
        Map<String, Integer> keywordFrequency = new HashMap<>();
        for (TrendSnapshot snapshot : chronological) {
            for (TrendSignal signal : snapshot.risingTopics()) {
                String key = signal.keyword().toLowerCase();
                keywordFrequency.merge(key, 1, Integer::sum);
            }
        }

        // 3. Pick top N most-frequently-appearing keywords
        List<String> topKeywords = new ArrayList<>(keywordFrequency.keySet());
        sortByFrequencyDescending(topKeywords, keywordFrequency);
        if (topKeywords.size() > TOP_KEYWORDS_LIMIT) {
            topKeywords = new ArrayList<>(topKeywords.subList(0, TOP_KEYWORDS_LIMIT));
        }

        // 4. Build count arrays — one per keyword, parallel to historyDates
        List<String> keywordNames = new ArrayList<>();
        List<List<Long>> keywordCounts = new ArrayList<>();

        for (String keyword : topKeywords) {
            keywordNames.add(toTitleCase(keyword));
            List<Long> counts = new ArrayList<>();

            for (TrendSnapshot snapshot : chronological) {
                long count = 0;
                for (TrendSignal signal : snapshot.risingTopics()) {
                    if (signal.keyword().toLowerCase().equals(keyword)) {
                        count = signal.current30d();
                        break;
                    }
                }
                counts.add(count);
            }

            keywordCounts.add(counts);
        }

        model.addAttribute("historyDates", historyDates);
        model.addAttribute("keywordNames", keywordNames);
        model.addAttribute("keywordCounts", keywordCounts);

        log.debug("buildChartData() | return=void, keywords={}, dates={}",
                  keywordNames.size(), historyDates.size());
    }

    private void sortByFrequencyDescending(List<String> keywords,
                                            Map<String, Integer> frequency) {
        int n = keywords.size();
        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1 - i; j++) {
                int freqA = frequency.getOrDefault(keywords.get(j), 0);
                int freqB = frequency.getOrDefault(keywords.get(j + 1), 0);
                if (freqA < freqB) {
                    String tmp = keywords.get(j);
                    keywords.set(j, keywords.get(j + 1));
                    keywords.set(j + 1, tmp);
                }
            }
        }
    }

    private <T> void reverseList(List<T> list) {
        int left = 0;
        int right = list.size() - 1;
        while (left < right) {
            T tmp = list.get(left);
            list.set(left, list.get(right));
            list.set(right, tmp);
            left++;
            right--;
        }
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

    private boolean hasFullAccess(Principal principal) {
        if (principal == null) {
            return false;
        }
        if (isAdmin(principal)) {
            return true;
        }
        Optional<Subscriber> subscriber = subscriberPort.findByEmail(principal.getName());
        if (subscriber.isEmpty()) {
            return false;
        }
        SubscriptionTier tier = subscriber.get().tier();
        return tier == SubscriptionTier.SUBSCRIBER || tier == SubscriptionTier.DEMO;
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

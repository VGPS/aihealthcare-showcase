package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.model.TrendSignal;
import com.wgblackmon.aihealthcare.domain.model.TrendSnapshot;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectTrendsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public (unauthenticated) controller for SEO-indexed trend analysis pages.
 *
 * <p>Serves two public routes:
 * <ul>
 *   <li>{@code GET /trends} — 302 redirect to the most recent snapshot's
 *       dated URL so every snapshot has a single canonical location.</li>
 *   <li>{@code GET /trends/{date}} — full trend detail for the snapshot
 *       whose {@code generatedAt} falls on the given UTC date (yyyy-MM-dd).
 *       Returns 404 when no snapshot exists for that date.</li>
 * </ul>
 *
 * <p>No authentication is required. The page is indexed by Google and listed
 * in {@code sitemap.xml}. {@code SeoModelAdvice} injects the canonical URL,
 * base URL, and noindex flag automatically; this controller sets
 * {@code pageTitle} and {@code pageDescription} for per-page meta tags.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-30
 * @updated 2026-09-30
 */
@Slf4j
@Controller
public class TrendPublicController {

    private static final DateTimeFormatter URL_DATE  = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TITLE_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.of("America/New_York"));
    private static final int CHART_LIMIT = 10;

    private final DetectTrendsUseCase detectTrendsUseCase;

    public TrendPublicController(DetectTrendsUseCase detectTrendsUseCase) {
        log.debug("TrendPublicController() | detectTrendsUseCase={}", detectTrendsUseCase);
        this.detectTrendsUseCase = detectTrendsUseCase;
    }

    /**
     * Redirects to the latest snapshot's dated URL.
     * Returns 404 if no snapshots exist yet.
     */
    @GetMapping("/trends")
    public String latest() {
        log.debug("latest()");

        Optional<TrendSnapshot> latestOpt = detectTrendsUseCase.getLatestSnapshot();
        if (latestOpt.isEmpty()) {
            log.debug("latest() | no snapshots found");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No trend data available yet");
        }

        String dateSlug = URL_DATE.format(
                latestOpt.get().generatedAt().atZone(ZoneOffset.UTC).toLocalDate());

        log.debug("latest() | return=redirect:/trends/{}", dateSlug);
        return "redirect:/trends/" + dateSlug;
    }

    /**
     * Renders the public trend detail page for the snapshot generated on the
     * given UTC date.
     *
     * @param date  date in {@code yyyy-MM-dd} format, matched against UTC generatedAt
     * @param model Thymeleaf model
     * @return the "trends-public-detail" view
     */
    @GetMapping("/trends/{date}")
    public String detail(@PathVariable String date, Model model) {
        log.debug("detail() | date={}", date);

        LocalDate localDate;
        try {
            localDate = LocalDate.parse(date, URL_DATE);
        } catch (DateTimeParseException e) {
            log.debug("detail() | unparseable date={}", date);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid date: " + date);
        }

        Instant start = localDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end   = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<TrendSnapshot> all = detectTrendsUseCase.getAllSnapshots();
        TrendSnapshot target = null;
        for (TrendSnapshot snap : all) {
            if (!snap.generatedAt().isBefore(start) && snap.generatedAt().isBefore(end)) {
                target = snap;
                break;
            }
        }

        if (target == null) {
            log.debug("detail() | no snapshot found for date={}", date);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No snapshot for " + date);
        }

        populateModel(target, date, model);

        log.debug("detail() | return=trends-public-detail");
        return "trends-public-detail";
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private void populateModel(TrendSnapshot snapshot, String dateSlug, Model model) {
        log.debug("populateModel() | dateSlug={}, rising={}, new={}",
                  dateSlug, snapshot.risingTopics().size(), snapshot.newTopics().size());

        List<TrendSignal> risingTopics = titleCaseSignals(snapshot.risingTopics());
        List<TrendSignal> newTopics    = titleCaseSignals(snapshot.newTopics());

        String displayDate  = TITLE_DATE.format(snapshot.generatedAt());
        String topKeyword   = risingTopics.isEmpty() ? "healthcare AI" : risingTopics.get(0).keyword();
        String pageTitle    = "Healthcare AI Trends — " + displayDate;
        String pageDesc     = "Healthcare AI trend analysis — " + displayDate + ": "
                + topKeyword + " leads " + snapshot.risingTopics().size() + " rising keywords. "
                + snapshot.newTopics().size() + " new topics detected across "
                + snapshot.totalKeywords() + " tracked keywords.";

        model.addAttribute("pageTitle",       pageTitle);
        model.addAttribute("pageDescription", pageDesc);
        model.addAttribute("risingTopics",    risingTopics);
        model.addAttribute("newTopics",       newTopics);
        model.addAttribute("totalKeywords",   snapshot.totalKeywords());
        model.addAttribute("generatedAt",     displayDate);
        model.addAttribute("windowDays",      snapshot.windowDays());
        model.addAttribute("dateSlug",        dateSlug);

        List<String> chartLabels = new ArrayList<>();
        List<Long>   chartData   = new ArrayList<>();
        int limit = Math.min(risingTopics.size(), CHART_LIMIT);
        for (int i = 0; i < limit; i++) {
            chartLabels.add(risingTopics.get(i).keyword());
            chartData.add(risingTopics.get(i).current30d());
        }
        model.addAttribute("chartLabels", chartLabels);
        model.addAttribute("chartData",   chartData);

        Map<String, String> articleDates = new HashMap<>();
        for (TrendSignal signal : risingTopics) {
            for (ScoredArticle scored : signal.topArticles()) {
                if (scored.publishedAt() != null) {
                    articleDates.put(scored.articleId(),
                                     DISPLAY_DATE.format(scored.publishedAt()));
                }
            }
        }
        model.addAttribute("articleDates", articleDates);

        log.debug("populateModel() | return=void, pageTitle={}", pageTitle);
    }

    private List<TrendSignal> titleCaseSignals(List<TrendSignal> signals) {
        List<TrendSignal> result = new ArrayList<>();
        for (TrendSignal s : signals) {
            result.add(new TrendSignal(
                    toTitleCase(s.keyword()),
                    s.current30d(), s.previous90d(),
                    s.baseline180d(), s.momentum(),
                    s.direction(), s.firstSeenAt(),
                    s.topArticles(), s.summary()));
        }
        return result;
    }

    private String toTitleCase(String keyword) {
        if (keyword == null || keyword.isBlank()) return keyword;
        String[] words = keyword.split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            String w = words[i];
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) sb.append(w.substring(1));
            }
        }
        return sb.toString();
    }
}

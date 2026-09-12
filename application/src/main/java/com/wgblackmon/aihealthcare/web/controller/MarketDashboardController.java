package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactAssessment;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.port.ProduceMarketDigestUseCase;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionQueryService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.PriceReactionSnapshot;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ReactionHorizon;
import com.wgblackmon.aihealthcare.domain.marketanalysis.RollupEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.WeeklyRollup;
import com.wgblackmon.aihealthcare.domain.marketanalysis.WeeklyRollupService;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller for the Market Digest dashboard at {@code /dashboard/market}.
 *
 * <p>Displays the latest (or a specific date's) AI-healthcare market digest with
 * qualifying entries ranked by market impact. Supports category filtering via
 * query parameter and applies tier gating (FREE users see the top 3 entries;
 * SUBSCRIBER/DEMO/ADMIN see all entries).
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET /dashboard/market} — shows the most recent digest</li>
 *   <li>{@code GET /dashboard/market/{date}} — shows the digest for a specific date</li>
 *   <li>{@code GET /dashboard/market/history} — browse all digest dates (tier-gated)</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-09-12
 */
@Slf4j
@Controller
public class MarketDashboardController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DisplayFormats.SHORT_DATE.withZone(ZoneOffset.UTC);

    private static final int FREE_LIMIT = 3;

    private final ProduceMarketDigestUseCase marketDigestService;
    private final WeeklyRollupService        weeklyRollupService;
    private final SubscriberPort             subscriberPort;
    private final PriceReactionQueryService  priceReactionQueryService;
    private final TierResolver               tierResolver;

    public MarketDashboardController(ProduceMarketDigestUseCase marketDigestService,
                                     WeeklyRollupService weeklyRollupService,
                                     SubscriberPort subscriberPort,
                                     PriceReactionQueryService priceReactionQueryService,
                                     @Nullable TierResolver tierResolver) {
        log.debug("MarketDashboardController() | marketDigestService={}, weeklyRollupService={}, "
                        + "subscriberPort={}, priceReactionQueryService={}, tierResolver={}",
                marketDigestService, weeklyRollupService, subscriberPort,
                priceReactionQueryService, tierResolver);
        this.marketDigestService = marketDigestService;
        this.weeklyRollupService = weeklyRollupService;
        this.subscriberPort      = subscriberPort;
        this.priceReactionQueryService = priceReactionQueryService;
        this.tierResolver = tierResolver;
        log.debug("MarketDashboardController() | return=void");
    }

    @GetMapping("/dashboard/market")
    public String marketDigestLatest(@RequestParam(required = false) String category,
                                     Principal principal,
                                     Model model) {
        log.debug("marketDigestLatest() | category={}", category);

        Optional<MarketDigest> latest = marketDigestService.findLatest();
        String result = populateModel(latest, category, principal, model);

        log.debug("marketDigestLatest() | return={}", result);
        return result;
    }

    @GetMapping("/dashboard/market/history")
    public String marketHistory(
            @RequestParam(required = false, defaultValue = "0") int days,
            Principal principal,
            Model model) {
        log.debug("marketHistory() | days={}", days);

        List<MarketDigest> all = marketDigestService.findAll();
        boolean fullAccess = tierResolver != null && tierResolver.hasFullAccess(principal);

        // Lookback filter — days=0 means show all
        List<MarketDigest> filtered = new ArrayList<>();
        if (days > 0) {
            LocalDate cutoff = LocalDate.now().minusDays(days);
            for (MarketDigest d : all) {
                if (!d.date().isBefore(cutoff)) {
                    filtered.add(d);
                }
            }
        } else {
            for (MarketDigest d : all) {
                filtered.add(d);
            }
        }

        // Tier gate applies to the already-filtered list
        int freeLimit = 7;
        List<MarketDigest> visible = filtered;
        if (!fullAccess && filtered.size() > freeLimit) {
            visible = filtered.subList(0, freeLimit);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (MarketDigest d : visible) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dateStr", DISPLAY_FMT.format(d.date().atStartOfDay(ZoneOffset.UTC)));
            row.put("dateIso", d.date().toString());
            row.put("entryCount", d.entries().size());
            row.put("topCategory", topCategory(d.entries()));
            row.put("generatedAt", DISPLAY_FMT.format(d.generatedAt()));
            rows.add(row);
        }

        model.addAttribute("digests", rows);
        model.addAttribute("totalDigests", all.size());
        model.addAttribute("filteredCount", filtered.size());
        model.addAttribute("hasHistory", !all.isEmpty());
        model.addAttribute("fullAccess", fullAccess);
        model.addAttribute("selectedDays", days);
        model.addAttribute("activePage", "market-history");

        log.debug("marketHistory() | return=market-digest-history ({} rows, days={})", rows.size(), days);
        return "market-digest-history";
    }

    @GetMapping("/dashboard/market/weekly")
    public String weeklyRollup(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf,
            Principal principal,
            Model model) {
        log.debug("weeklyRollup() | weekOf={}", weekOf);

        boolean fullAccess = tierResolver != null && tierResolver.hasFullAccess(principal);

        if (weekOf == null) {
            // Default to the most recent Monday
            LocalDate today = LocalDate.now();
            weekOf = today.minusDays(today.getDayOfWeek().getValue() - 1);
        }

        WeeklyRollup rollup = weeklyRollupService.buildRollup(weekOf);

        List<Map<String, Object>> entryRows = new ArrayList<>();
        for (RollupEntry re : rollup.entries()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("headline",          re.representative().newsItem().headline());
            row.put("summary",           re.representative().newsItem().summary());
            row.put("category",          re.representative().category().name());
            row.put("rank",              re.bestRank().value());
            row.put("factClassification", re.factClassification().name());
            row.put("confirmed",         re.factClassification() == FactClassification.CONFIRMED);
            row.put("occurrenceCount",   re.occurrenceCount());
            row.put("companies",         companyLabels(re.representative().affectedCompanies()));
            row.put("reactions",         reactionBadges(re.representative()));
            row.put("sourceUrls",        re.representative().newsItem().sourceUrls());
            entryRows.add(row);
        }

        // Tier gate: FREE users see top 5
        List<Map<String, Object>> visible = entryRows;
        if (!fullAccess && entryRows.size() > FREE_LIMIT) {
            visible = entryRows.subList(0, FREE_LIMIT);
        }

        model.addAttribute("entries",     visible);
        model.addAttribute("totalEntries", rollup.entries().size());
        model.addAttribute("weekOf",      DISPLAY_FMT.format(weekOf.atStartOfDay(ZoneOffset.UTC)));
        model.addAttribute("weekEnd",     DISPLAY_FMT.format(weekOf.plusDays(6).atStartOfDay(ZoneOffset.UTC)));
        model.addAttribute("weekOfIso",   weekOf.toString());
        model.addAttribute("prevWeekIso", weekOf.minusDays(7).toString());
        model.addAttribute("nextWeekIso", weekOf.plusDays(7).toString());
        model.addAttribute("fullAccess",  fullAccess);
        model.addAttribute("activePage",  "market-weekly");

        String result = "market-digest-weekly";
        log.debug("weeklyRollup() | return={}", result);
        return result;
    }

    @GetMapping("/dashboard/market/{date}")
    public String marketDigestByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String category,
            Principal principal,
            Model model) {
        log.debug("marketDigestByDate() | date={}, category={}", date, category);

        Optional<MarketDigest> digest = marketDigestService.findByDate(date);
        String result = populateModel(digest, category, principal, model);

        log.debug("marketDigestByDate() | return={}", result);
        return result;
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private String populateModel(Optional<MarketDigest> digestOpt,
                                 String category,
                                 Principal principal,
                                 Model model) {
        boolean fullAccess = tierResolver != null && tierResolver.hasFullAccess(principal);

        if (digestOpt.isEmpty()) {
            model.addAttribute("digest", null);
            model.addAttribute("entries", List.of());
            model.addAttribute("filterCategory", category);
            model.addAttribute("digestDate", null);
            model.addAttribute("fullAccess", fullAccess);
            model.addAttribute("categoryCounts", Map.of());
            model.addAttribute("totalEntries", 0);
            model.addAttribute("activePage", "market");
            return "market-digest";
        }

        MarketDigest digest = digestOpt.get();
        String dateLabel = DISPLAY_FMT.format(digest.date().atStartOfDay(ZoneOffset.UTC));

        List<MarketDigestEntry> all = digest.entries();

        // Category filter
        List<MarketDigestEntry> filtered = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            NewsCategory filterEnum = parseCategory(category);
            for (MarketDigestEntry entry : all) {
                if (filterEnum != null && entry.category() == filterEnum) {
                    filtered.add(entry);
                }
            }
        } else {
            for (MarketDigestEntry entry : all) {
                filtered.add(entry);
            }
        }

        // Category counts (on unfiltered set)
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        for (MarketDigestEntry entry : all) {
            String cat = entry.category().name();
            categoryCounts.put(cat, categoryCounts.getOrDefault(cat, 0) + 1);
        }

        // Tier gate
        if (!fullAccess && filtered.size() > FREE_LIMIT) {
            filtered = filtered.subList(0, FREE_LIMIT);
        }

        List<Map<String, Object>> entries = toDisplayList(filtered);

        model.addAttribute("entries", entries);
        model.addAttribute("filterCategory", category);
        model.addAttribute("digestDate", dateLabel);
        model.addAttribute("digestLocalDate", digest.date());
        model.addAttribute("fullAccess", fullAccess);
        model.addAttribute("categoryCounts", categoryCounts);
        model.addAttribute("totalEntries", all.size());
        model.addAttribute("activePage", "market");

        return "market-digest";
    }

    private List<Map<String, Object>> toDisplayList(List<MarketDigestEntry> entries) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MarketDigestEntry entry : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("headline", entry.newsItem().headline());
            map.put("summary", entry.newsItem().summary());
            map.put("category", entry.category().name());
            map.put("factClassification", entry.factClassification().name());
            map.put("rank", entry.rank().value());
            map.put("publishedAt", DISPLAY_FMT.format(entry.newsItem().publishedAt()));
            map.put("dealSizeUsd", entry.newsItem().dealSizeUsd());
            map.put("sourceUrls", entry.newsItem().sourceUrls());
            map.put("companies", companyLabels(entry.affectedCompanies()));
            map.put("impactSummary", firstImpactRationale(entry.impactAssessments()));
            map.put("reactions", reactionBadges(entry));
            result.add(map);
        }
        return result;
    }

    private List<Map<String, Object>> reactionBadges(MarketDigestEntry entry) {
        List<Map<String, Object>> badges = new ArrayList<>();
        for (AffectedCompany company : entry.affectedCompanies()) {
            if (company.tickerSymbol() == null || company.tickerSymbol().isBlank()) {
                continue;
            }

            List<PriceReactionSnapshot> snapshots = priceReactionQueryService.findReactions(
                    company.tickerSymbol(), entry.newsItem().publishedAt());
            Map<ReactionHorizon, PriceReactionSnapshot> byHorizon = new EnumMap<>(ReactionHorizon.class);
            for (PriceReactionSnapshot snapshot : snapshots) {
                byHorizon.put(snapshot.horizon(), snapshot);
            }

            for (ReactionHorizon horizon : ReactionHorizon.values()) {
                PriceReactionSnapshot snapshot = byHorizon.get(horizon);
                if (snapshot == null) {
                    continue;
                }
                Map<String, Object> badge = new LinkedHashMap<>();
                badge.put("label", company.tickerSymbol() + " " + horizonLabel(horizon)
                        + " " + formatPct(snapshot.pctChange()));
                badge.put("positive", snapshot.pctChange().compareTo(BigDecimal.ZERO) >= 0);
                badges.add(badge);
            }
        }
        return badges;
    }

    private String horizonLabel(ReactionHorizon horizon) {
        switch (horizon) {
            case ONE_HOUR:  return "1h";
            case FOUR_HOUR: return "4h";
            case ONE_DAY:   return "1d";
            case THREE_DAY: return "3d";
            default:        return horizon.name();
        }
    }

    private String formatPct(BigDecimal pct) {
        String sign = pct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return sign + pct.toPlainString() + "%";
    }

    private List<String> companyLabels(List<AffectedCompany> companies) {
        List<String> labels = new ArrayList<>();
        for (AffectedCompany c : companies) {
            if (c.tickerSymbol() != null && !c.tickerSymbol().isBlank()) {
                labels.add(c.name() + " (" + c.tickerSymbol() + ")");
            } else {
                labels.add(c.name());
            }
        }
        return labels;
    }

    private String firstImpactRationale(List<ImpactAssessment> assessments) {
        if (assessments == null || assessments.isEmpty()) {
            return "";
        }
        return assessments.get(0).rationale();
    }

    private String topCategory(List<MarketDigestEntry> entries) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MarketDigestEntry e : entries) {
            String cat = e.category().name();
            counts.put(cat, counts.getOrDefault(cat, 0) + 1);
        }
        String top = null;
        int max = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                top = e.getKey();
            }
        }
        return top;
    }

    private NewsCategory parseCategory(String category) {
        if (category == null) {
            return null;
        }
        switch (category.toUpperCase()) {
            case "EARNINGS":         return NewsCategory.EARNINGS;
            case "REGULATORY":       return NewsCategory.REGULATORY;
            case "FUNDING":          return NewsCategory.FUNDING;
            case "M_AND_A":          return NewsCategory.M_AND_A;
            case "MAJOR_PARTNERSHIP": return NewsCategory.MAJOR_PARTNERSHIP;
            default:                 return null;
        }
    }
}

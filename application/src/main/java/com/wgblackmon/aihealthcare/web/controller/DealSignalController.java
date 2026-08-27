package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DealContext;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectDealSignalsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller for the deal signals dashboard at
 * {@code /dashboard/deals} and deal detail at {@code /dashboard/deals/{signalId}}.
 *
 * <p>Displays recent deal signals (funding, acquisitions, partnerships,
 * IPOs, product launches) detected in harvested articles. Supports
 * filtering by type, tier gating, and cross-referenced deal detail views.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-08-04
 * @updated 2026-08-26
 */
@Slf4j
@Controller
public class DealSignalController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneOffset.UTC);

    private static final int FREE_LIMIT = 10;
    private static final int PAGE_SIZE = 25;

    private final DetectDealSignalsUseCase detectDealSignalsUseCase;
    private final SubscriberPort subscriberPort;

    public DealSignalController(DetectDealSignalsUseCase detectDealSignalsUseCase,
                                 SubscriberPort subscriberPort) {
        log.debug("DealSignalController() | detectDealSignalsUseCase={}, subscriberPort={}",
                detectDealSignalsUseCase.getClass().getSimpleName(),
                subscriberPort.getClass().getSimpleName());
        this.detectDealSignalsUseCase = detectDealSignalsUseCase;
        this.subscriberPort = subscriberPort;
    }

    @GetMapping({"/dashboard/deals", "/dashboard/deals/"})
    public String dealsPage(@RequestParam(required = false) String type,
                             @RequestParam(defaultValue = "0") int page,
                             Principal principal,
                             Model model) {
        log.debug("dealsPage() | type={}, page={}", type, page);

        boolean fullAccess = hasFullAccess(principal);
        int currentPage = fullAccess ? Math.max(0, page) : 0;

        // Peek-ahead: fetch PAGE_SIZE+1 to detect next page without a COUNT query
        int fetchSize = fullAccess ? PAGE_SIZE + 1 : FREE_LIMIT;

        List<DealSignal> signals;
        DealSignalType filterType = (type != null && !type.isBlank()) ? parseDealType(type) : null;
        if (filterType != null) {
            signals = detectDealSignalsUseCase.getSignalsByType(filterType, fetchSize, currentPage);
        } else {
            signals = detectDealSignalsUseCase.getRecentSignals(fetchSize, currentPage);
        }

        boolean hasNext = fullAccess && signals.size() > PAGE_SIZE;
        if (hasNext) {
            signals = signals.subList(0, PAGE_SIZE);
        }

        List<DealSignal> deduped = deduplicateByCompanyTypeAndDay(signals);

        List<Map<String, Object>> signalList = new ArrayList<>();
        for (DealSignal signal : deduped) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("signalId", signal.signalId());
            entry.put("title", signal.title());
            entry.put("signalType", signal.signalType().name());
            entry.put("companyName", signal.companyName());
            entry.put("summary", signal.summary());
            entry.put("confidence", String.format("%.0f%%", signal.confidence() * 100));
            entry.put("detectedAt", DISPLAY_FMT.format(signal.detectedAt()));
            entry.put("dealAmount", signal.dealAmount());
            entry.put("counterpartyName", signal.counterpartyName());
            entry.put("sourceUrl", signal.sourceUrl());
            signalList.add(entry);
        }

        // 30-day vs prior-30-day aggregate counts (from DB, independent of current page)
        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(30, ChronoUnit.DAYS);
        Instant sixtyDaysAgo = now.minus(60, ChronoUnit.DAYS);
        Map<String, Long> current30d = detectDealSignalsUseCase.getTypeStats(thirtyDaysAgo, now);
        Map<String, Long> prior30d   = detectDealSignalsUseCase.getTypeStats(sixtyDaysAgo, thirtyDaysAgo);

        List<Map<String, Object>> typeStats  = buildTypeStats(current30d, prior30d);
        List<Map<String, Object>> ratioStats = buildRatioStats(current30d, prior30d);
        Map<String, Object> velocity         = buildVelocity(current30d, prior30d);

        model.addAttribute("signals", signalList);
        model.addAttribute("totalSignals", signalList.size());
        model.addAttribute("typeStats", typeStats);
        model.addAttribute("ratioStats", ratioStats);
        model.addAttribute("velocity", velocity);
        model.addAttribute("hasPriorData", !prior30d.isEmpty());
        model.addAttribute("filterType", type);
        model.addAttribute("fullAccess", fullAccess);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("hasPrev", currentPage > 0);
        model.addAttribute("hasNext", hasNext);
        model.addAttribute("activePage", "deals");

        log.debug("dealsPage() | return=deals ({} signals, page={}, hasNext={})",
                signalList.size(), currentPage, hasNext);
        return "deals";
    }

    @GetMapping("/dashboard/deals/{signalId}")
    public String dealDetail(@PathVariable String signalId,
                              Principal principal,
                              Model model) {
        log.debug("dealDetail() | signalId={}", signalId);

        DealContext context = detectDealSignalsUseCase.getSignalWithContext(signalId);
        if (context == null) {
            log.debug("dealDetail() | signal not found, returning to deals list");
            return "redirect:/dashboard/deals";
        }

        DealSignal signal = context.signal();
        Map<String, Object> signalMap = new LinkedHashMap<>();
        signalMap.put("signalId", signal.signalId());
        signalMap.put("title", signal.title());
        signalMap.put("signalType", signal.signalType().name());
        signalMap.put("companyName", signal.companyName());
        signalMap.put("summary", signal.summary());
        signalMap.put("confidence", String.format("%.0f%%", signal.confidence() * 100));
        signalMap.put("detectedAt", DISPLAY_FMT.format(signal.detectedAt()));
        signalMap.put("dealAmount", signal.dealAmount());
        signalMap.put("counterpartyName", signal.counterpartyName());
        signalMap.put("sourceUrl", signal.sourceUrl());
        signalMap.put("llmAnalysis", signal.llmAnalysis());
        signalMap.put("articleId", signal.articleId());

        model.addAttribute("signal", signalMap);
        model.addAttribute("sentiment", context.sentiment());
        model.addAttribute("framework", context.framework());
        model.addAttribute("regulatoryEvents", context.regulatoryEvents());
        model.addAttribute("companyProfile", context.companyProfile());
        model.addAttribute("activePage", "deals");

        log.debug("dealDetail() | return=deals-detail for {}", signalId);
        return "deals-detail";
    }

    private static final String[] TYPE_NAMES   = {"FUNDING", "ACQUISITION", "PARTNERSHIP", "IPO", "PRODUCT_LAUNCH"};
    private static final String[] TYPE_COLORS  = {"green",   "red",         "blue",        "purple", "yellow"};

    private List<Map<String, Object>> buildTypeStats(Map<String, Long> current, Map<String, Long> prior) {
        List<Map<String, Object>> stats = new ArrayList<>();
        for (int i = 0; i < TYPE_NAMES.length; i++) {
            String typeName = TYPE_NAMES[i];
            long cur  = current.getOrDefault(typeName, 0L);
            long prev = prior.getOrDefault(typeName, 0L);

            String changeLabel;
            String changeClass;
            if (prev == 0) {
                changeLabel = "—";
                changeClass = "text-gray-400";
            } else {
                long diff = cur - prev;
                long pct  = Math.round(100.0 * diff / prev);
                changeLabel = (diff >= 0 ? "+" : "") + pct + "%";
                changeClass = diff > 0 ? "text-green-600 font-semibold"
                                       : (diff < 0 ? "text-red-600 font-semibold" : "text-gray-500");
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type",        typeName);
            entry.put("label",       typeName.replace("_", " "));
            entry.put("color",       TYPE_COLORS[i]);
            entry.put("currentCount", cur);
            entry.put("priorCount",  prev > 0 ? prev : null);
            entry.put("changeLabel", changeLabel);
            entry.put("changeClass", changeClass);
            stats.add(entry);
        }
        return stats;
    }

    private List<Map<String, Object>> buildRatioStats(Map<String, Long> cur, Map<String, Long> prev) {
        List<Map<String, Object>> ratios = new ArrayList<>();

        long curPartner  = cur.getOrDefault("PARTNERSHIP",    0L);
        long curAcq      = cur.getOrDefault("ACQUISITION",    0L);
        long curFunding  = cur.getOrDefault("FUNDING",        0L);
        long curIpo      = cur.getOrDefault("IPO",            0L);
        long curLaunch   = cur.getOrDefault("PRODUCT_LAUNCH", 0L);

        long prvPartner  = prev.getOrDefault("PARTNERSHIP",    0L);
        long prvAcq      = prev.getOrDefault("ACQUISITION",    0L);
        long prvFunding  = prev.getOrDefault("FUNDING",        0L);
        long prvIpo      = prev.getOrDefault("IPO",            0L);
        long prvLaunch   = prev.getOrDefault("PRODUCT_LAUNCH", 0L);

        ratios.add(ratio("Partnership : Acquisition",
                "Strategic alignment vs ownership intent. Are companies choosing low-commitment deals or taking full control?",
                "High (>2:1) — Market is uncertain or targets are expensive; buyers prefer partnerships over ownership risk.",
                "Low (<1:1) — Acquirers are confident. They're taking ownership, not testing the waters.",
                curPartner, curAcq, prvPartner, prvAcq));

        ratios.add(ratio("Funding : Acquisition",
                "Ecosystem growth vs consolidation. Is new money flowing in faster than companies are being absorbed?",
                "High (>3:1) — Early-stage ecosystem is healthy; capital is backing new companies faster than large players can acquire them.",
                "Low (<1.5:1) — Consolidation is underway; large players are absorbing the field. Late-cycle signal.",
                curFunding, curAcq, prvFunding, prvAcq));

        ratios.add(ratio("Product Launch : Acquisition",
                "Build vs Buy. Are companies shipping their own technology or buying it from others?",
                "High (>3:1) — R&D is productive; companies believe they can out-build competitors.",
                "Low (<1:1) — Buying is faster or cheaper than building right now. May signal depressed target valuations.",
                curLaunch, curAcq, prvLaunch, prvAcq));

        ratios.add(ratio("Funding : (Acquisition + IPO)",
                "Capital formation vs capital exit. Money entering the ecosystem vs companies being absorbed or going public.",
                "High (>2:1) — More capital is being deployed into new ventures than leaving via exits. Early-stage health.",
                "Low (<1:1) — Exits are outpacing new investment. Often a late-cycle or market-contraction signal.",
                curFunding, curAcq + curIpo, prvFunding, prvAcq + prvIpo));

        ratios.add(ratio("IPO : Acquisition",
                "Exit path preference. Are companies choosing public markets or selling to a strategic buyer?",
                "High (>0.5:1) — Founders and investors believe public markets will reward them; IPO window is open.",
                "Low (<0.1:1) — M&A dominates exits; founders or investors prefer the certainty of a strategic sale over public market risk.",
                curIpo, curAcq, prvIpo, prvAcq));

        return ratios;
    }

    private Map<String, Object> buildVelocity(Map<String, Long> cur, Map<String, Long> prev) {
        long curTotal = 0L;
        for (long v : cur.values()) { curTotal += v; }
        long prvTotal = 0L;
        for (long v : prev.values()) { prvTotal += v; }

        String changeLabel;
        String changeClass;
        if (prvTotal == 0) {
            changeLabel = "—";
            changeClass = "text-gray-400";
        } else {
            long diff = curTotal - prvTotal;
            long pct  = Math.round(100.0 * diff / prvTotal);
            changeLabel = (diff >= 0 ? "+" : "") + pct + "%";
            changeClass = diff > 0 ? "text-green-600 font-semibold"
                                   : (diff < 0 ? "text-red-500 font-semibold" : "text-gray-500");
        }

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("currentTotal", curTotal);
        v.put("priorTotal",   prvTotal > 0 ? prvTotal : null);
        v.put("changeLabel",  changeLabel);
        v.put("changeClass",  changeClass);
        return v;
    }

    private Map<String, Object> ratio(String name, String headline,
                                       String highMeans, String lowMeans,
                                       long curNum, long curDen,
                                       long prvNum, long prvDen) {
        String current = formatRatio(curNum, curDen);
        String prior   = (prvNum == 0 && prvDen == 0) ? null : formatRatio(prvNum, prvDen);

        String direction  = "—";
        String dirClass   = "text-gray-400";
        if (prior != null && curDen > 0 && prvDen > 0) {
            double curVal = (double) curNum / curDen;
            double prvVal = (double) prvNum / prvDen;
            if      (curVal > prvVal * 1.05) { direction = "↑"; dirClass = "text-green-600 font-bold"; }
            else if (curVal < prvVal * 0.95) { direction = "↓"; dirClass = "text-red-500 font-bold"; }
            else                             { direction = "→"; dirClass = "text-gray-500"; }
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name",      name);
        m.put("headline",  headline);
        m.put("highMeans", highMeans);
        m.put("lowMeans",  lowMeans);
        m.put("current",   current);
        m.put("prior",     prior);
        m.put("direction", direction);
        m.put("dirClass",  dirClass);
        return m;
    }

    private String formatRatio(long numerator, long denominator) {
        if (denominator == 0) {
            return numerator > 0 ? numerator + ":0" : "—";
        }
        return String.format("%.1f:1", (double) numerator / denominator);
    }

    private boolean hasFullAccess(Principal principal) {
        if (principal == null) {
            return false;
        }
        if (principal instanceof Authentication) {
            Authentication auth = (Authentication) principal;
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                    return true;
                }
            }
        }
        Optional<Subscriber> subscriber = subscriberPort.findByEmail(principal.getName());
        if (subscriber.isPresent()) {
            SubscriptionTier tier = subscriber.get().tier();
            return tier == SubscriptionTier.SUBSCRIBER || tier == SubscriptionTier.DEMO;
        }
        return false;
    }

    private List<DealSignal> deduplicateByCompanyTypeAndDay(List<DealSignal> signals) {
        Map<String, DealSignal> bestByKey = new LinkedHashMap<>();
        for (DealSignal signal : signals) {
            String company = signal.companyName() != null
                    ? signal.companyName().toLowerCase(Locale.ENGLISH).trim() : "";
            String dayStr = signal.detectedAt().toString().substring(0, 10);
            String key = company + "|" + signal.signalType().name() + "|" + dayStr;

            DealSignal existing = bestByKey.get(key);
            if (existing == null || signal.confidence() > existing.confidence()) {
                bestByKey.put(key, signal);
            }
        }
        return new ArrayList<>(bestByKey.values());
    }

    private DealSignalType parseDealType(String type) {
        if (type == null) {
            return null;
        }
        switch (type.toUpperCase()) {
            case "FUNDING":        return DealSignalType.FUNDING;
            case "ACQUISITION":    return DealSignalType.ACQUISITION;
            case "PARTNERSHIP":    return DealSignalType.PARTNERSHIP;
            case "IPO":            return DealSignalType.IPO;
            case "PRODUCT_LAUNCH": return DealSignalType.PRODUCT_LAUNCH;
            default:               return null;
        }
    }
}

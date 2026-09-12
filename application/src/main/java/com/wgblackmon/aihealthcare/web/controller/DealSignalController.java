package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DealContext;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectDealSignalsUseCase;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
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

/**
 * Thymeleaf controller for the deal signals dashboard at
 * {@code /dashboard/deals} and deal detail at {@code /dashboard/deals/{signalId}}.
 *
 * <p>Displays recent deal signals (funding, acquisitions, partnerships,
 * IPOs, product launches) detected in harvested articles. Supports
 * filtering by type, tier gating, and cross-referenced deal detail views.
 *
 * @author  Bill Blackmon
 * @version 1.3
 * @since   2026-08-04
 * @updated 2026-09-12
 */
@Slf4j
@Controller
public class DealSignalController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DisplayFormats.TIMESTAMP_24H.withZone(ZoneOffset.UTC);

    private static final int FREE_LIMIT = 10;
    private static final int PAGE_SIZE = 25;

    private final DetectDealSignalsUseCase detectDealSignalsUseCase;
    private final SubscriberPort subscriberPort;
    private final TierResolver tierResolver;

    public DealSignalController(DetectDealSignalsUseCase detectDealSignalsUseCase,
                                 SubscriberPort subscriberPort,
                                 @Nullable TierResolver tierResolver) {
        log.debug("DealSignalController() | detectDealSignalsUseCase={}, subscriberPort={}, tierResolver={}",
                detectDealSignalsUseCase, subscriberPort, tierResolver);
        this.detectDealSignalsUseCase = detectDealSignalsUseCase;
        this.subscriberPort = subscriberPort;
        this.tierResolver = tierResolver;
    }

    @GetMapping({"/dashboard/deals", "/dashboard/deals/"})
    public String dealsPage(@RequestParam(required = false) String type,
                             @RequestParam(defaultValue = "0") int page,
                             Principal principal,
                             Model model) {
        log.debug("dealsPage() | type={}, page={}", type, page);

        boolean fullAccess = tierResolver != null
                ? tierResolver.hasFullAccess(principal)
                : hasFullAccessFallback(principal);
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
            entry.put("signalTypeLabel", toDisplayLabel(signal.signalType()));
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
        log.debug("dealDetail() | signalId={}, principal={}", signalId, principal != null ? principal.getName() : "anonymous");

        boolean hasEnterprise = tierResolver != null
                ? tierResolver.hasEnterpriseAccess(principal)
                : hasFullAccessFallback(principal);
        if (!hasEnterprise) {
            log.debug("dealDetail() | upgrade required for signalId={}", signalId);
            model.addAttribute("upgradeRequired", true);
            model.addAttribute("activePage", "deals");
            return "deals-detail";
        }

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
        model.addAttribute("upgradeRequired", false);
        model.addAttribute("activePage", "deals");

        log.debug("dealDetail() | return=deals-detail for {}", signalId);
        return "deals-detail";
    }

    private static final String[] TYPE_NAMES   = {"FUNDING", "ACQUISITION", "PARTNERSHIP", "IPO", "PRODUCT_LAUNCH"};
    private static final String[] TYPE_LABELS  = {"Funding", "Acquisition", "Partnership", "IPO", "Product Launch"};
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
            entry.put("label",       TYPE_LABELS[i]);
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
                "Strategic alignment vs ownership. Are companies forming alliances or taking full control?",
                "When high — Market is cautious or target prices are steep. Companies prefer low-commitment deals over taking on ownership risk.",
                "When low — Acquirers are confident and moving decisively. Full ownership, not just a handshake agreement.",
                curPartner, curAcq, prvPartner, prvAcq));

        ratios.add(ratio("Funding : Acquisition",
                "Ecosystem growth vs consolidation. Is new investment flowing in faster than companies are being absorbed?",
                "When high — Fresh capital is backing new companies faster than larger players can acquire them. Healthy early-stage activity.",
                "When low — The market is consolidating. Larger players are absorbing smaller ones at a faster pace than new money is arriving.",
                curFunding, curAcq, prvFunding, prvAcq));

        ratios.add(ratio("Product Launch : Acquisition",
                "Build vs Buy. Are companies shipping their own technology or acquiring it from others?",
                "When high — Companies believe they can out-build the competition. R&D is paying off.",
                "When low — Buying is faster or cheaper than building right now. Can signal that acquisition targets are available at reasonable prices.",
                curLaunch, curAcq, prvLaunch, prvAcq));

        ratios.add(ratio("Funding : (Acquisitions + IPOs)",
                "Money entering the ecosystem vs money leaving it through exits.",
                "When high — More capital is flowing into new ventures than companies are exiting. A sign of early-stage market health.",
                "When low — Exits are outpacing new investment. Can signal a late-cycle market or a slowdown in new company formation.",
                curFunding, curAcq + curIpo, prvFunding, prvAcq + prvIpo));

        ratios.add(ratio("IPO : Acquisition",
                "Exit path preference. Are founders choosing public markets or selling to a strategic buyer?",
                "When high — Founders and investors believe the public markets will reward them. The IPO window is open.",
                "When low — Private sales dominate. Founders or investors prefer the certainty of a strategic buyer over public market risk.",
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

    private String toDisplayLabel(DealSignalType type) {
        switch (type) {
            case FUNDING:        return "Funding";
            case ACQUISITION:    return "Acquisition";
            case PARTNERSHIP:    return "Partnership";
            case IPO:            return "IPO";
            case PRODUCT_LAUNCH: return "Product Launch";
            default:             return type.name();
        }
    }

    private String formatRatio(long numerator, long denominator) {
        if (denominator == 0) {
            return numerator > 0 ? numerator + ":0" : "—";
        }
        return String.format("%.1f:1", (double) numerator / denominator);
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

    /**
     * Fallback when TierResolver is absent (e.g. in unit tests).
     * Checks ROLE_ADMIN authority and subscriberPort tier.
     */
    private boolean hasFullAccessFallback(Principal principal) {
        if (principal == null) {
            return false;
        }
        if (principal instanceof Authentication auth) {
            for (GrantedAuthority ga : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(ga.getAuthority())) {
                    return true;
                }
            }
        }
        return subscriberPort.findByEmail(principal.getName())
                .map(Subscriber::tier)
                .map(t -> t == SubscriptionTier.SUBSCRIBER || t == SubscriptionTier.DEMO || t == SubscriptionTier.ENTERPRISE)
                .orElse(false);
    }
}

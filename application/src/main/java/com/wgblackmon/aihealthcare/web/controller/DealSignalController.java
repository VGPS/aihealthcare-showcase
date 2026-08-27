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

        List<Map<String, Object>> typeStats = buildTypeStats(current30d, prior30d);
        String partnerAcqRatio      = formatRatio(current30d.getOrDefault("PARTNERSHIP", 0L),
                                                  current30d.getOrDefault("ACQUISITION", 0L));
        String priorPartnerAcqRatio = formatRatio(prior30d.getOrDefault("PARTNERSHIP", 0L),
                                                  prior30d.getOrDefault("ACQUISITION", 0L));

        model.addAttribute("signals", signalList);
        model.addAttribute("totalSignals", signalList.size());
        model.addAttribute("typeStats", typeStats);
        model.addAttribute("partnerAcqRatio", partnerAcqRatio);
        model.addAttribute("priorPartnerAcqRatio", priorPartnerAcqRatio);
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

    private String formatRatio(long partnership, long acquisition) {
        if (acquisition == 0) {
            return partnership > 0 ? partnership + ":0" : "—";
        }
        return String.format("%.1f:1", (double) partnership / acquisition);
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

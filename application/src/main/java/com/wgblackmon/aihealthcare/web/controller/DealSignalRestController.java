package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DealContext;
import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectDealSignalsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * REST controller for deal signal endpoints.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-06
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/deals")
public class DealSignalRestController {

    private final DetectDealSignalsUseCase detectDealSignalsUseCase;

    public DealSignalRestController(DetectDealSignalsUseCase detectDealSignalsUseCase) {
        log.debug("DealSignalRestController() | detectDealSignalsUseCase={}", detectDealSignalsUseCase.getClass().getSimpleName());
        this.detectDealSignalsUseCase = detectDealSignalsUseCase;
    }

    @GetMapping
    public ResponseEntity<?> getRecentSignals(@RequestParam(defaultValue = "50") int limit,
                                               @RequestParam(required = false) String type) {
        log.debug("getRecentSignals() | limit={}, type={}", limit, type);

        List<DealSignal> signals;
        if (type != null && !type.isBlank()) {
            DealSignalType filterType = parseDealType(type);
            if (filterType != null) {
                signals = detectDealSignalsUseCase.getSignalsByType(filterType, limit);
            } else {
                signals = detectDealSignalsUseCase.getRecentSignals(limit);
            }
        } else {
            signals = detectDealSignalsUseCase.getRecentSignals(limit);
        }

        List<DealSignal> deduped = deduplicateByCompanyTypeAndDay(signals);
        List<Map<String, Object>> result = new ArrayList<>();
        for (DealSignal signal : deduped) {
            result.add(toMap(signal));
        }
        log.debug("getRecentSignals() | return={} signals", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{signalId}")
    public ResponseEntity<?> getSignalDetail(@PathVariable String signalId) {
        log.debug("getSignalDetail() | signalId={}", signalId);

        DealContext context = detectDealSignalsUseCase.getSignalWithContext(signalId);
        if (context == null) {
            log.debug("getSignalDetail() | return=404");
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signal", toMap(context.signal()));
        result.put("hasSentiment", context.sentiment() != null);
        if (context.sentiment() != null) {
            result.put("sentimentLabel", context.sentiment().overallSentiment().name());
            result.put("sentimentScore", context.sentiment().sentimentScore());
        }
        result.put("hasFramework", context.framework() != null);
        if (context.framework() != null) {
            result.put("frameworkScore", context.framework().overallScore());
        }
        result.put("regulatoryEventCount", context.regulatoryEvents().size());
        result.put("hasProfile", context.companyProfile() != null);

        log.debug("getSignalDetail() | return=context for {}", signalId);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/detect")
    public ResponseEntity<?> triggerDetection() {
        log.debug("triggerDetection()");
        List<DealSignal> signals = detectDealSignalsUseCase.detectSignals();
        log.debug("triggerDetection() | return={} signals", signals.size());
        return ResponseEntity.ok(Map.of("detected", signals.size()));
    }

    private Map<String, Object> toMap(DealSignal signal) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("signalId", signal.signalId());
        entry.put("articleId", signal.articleId());
        entry.put("title", signal.title());
        entry.put("signalType", signal.signalType().name());
        entry.put("companyName", signal.companyName());
        entry.put("summary", signal.summary());
        entry.put("confidence", signal.confidence());
        entry.put("detectedAt", signal.detectedAt().toString());
        entry.put("dealAmount", signal.dealAmount());
        entry.put("counterpartyName", signal.counterpartyName());
        entry.put("sourceUrl", signal.sourceUrl());
        entry.put("llmAnalysis", signal.llmAnalysis());
        return entry;
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

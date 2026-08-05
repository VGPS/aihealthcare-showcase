package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectDealSignalsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for deal signal endpoints.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
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
    public ResponseEntity<?> getRecentSignals(@RequestParam(defaultValue = "50") int limit) {
        log.debug("getRecentSignals() | limit={}", limit);
        List<DealSignal> signals = detectDealSignalsUseCase.getRecentSignals(limit);
        List<Map<String, Object>> result = new ArrayList<>();
        for (DealSignal signal : signals) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("signalId", signal.signalId());
            entry.put("articleId", signal.articleId());
            entry.put("title", signal.title());
            entry.put("signalType", signal.signalType().name());
            entry.put("companyName", signal.companyName());
            entry.put("summary", signal.summary());
            entry.put("confidence", signal.confidence());
            entry.put("detectedAt", signal.detectedAt().toString());
            result.add(entry);
        }
        log.debug("getRecentSignals() | return={} signals", result.size());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/detect")
    public ResponseEntity<?> triggerDetection() {
        log.debug("triggerDetection()");
        List<DealSignal> signals = detectDealSignalsUseCase.detectSignals();
        log.debug("triggerDetection() | return={} signals", signals.size());
        return ResponseEntity.ok(Map.of("detected", signals.size()));
    }
}

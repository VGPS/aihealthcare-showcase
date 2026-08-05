package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.port.inbound.DetectDealSignalsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Thymeleaf controller for the deal signals dashboard at
 * {@code /dashboard/deals}.
 *
 * <p>Displays recent deal signals (funding, acquisitions, partnerships,
 * IPOs, product launches) detected in harvested articles.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Slf4j
@Controller
public class DealSignalController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneOffset.UTC);

    private final DetectDealSignalsUseCase detectDealSignalsUseCase;

    public DealSignalController(DetectDealSignalsUseCase detectDealSignalsUseCase) {
        log.debug("DealSignalController() | detectDealSignalsUseCase={}", detectDealSignalsUseCase.getClass().getSimpleName());
        this.detectDealSignalsUseCase = detectDealSignalsUseCase;
    }

    @GetMapping("/dashboard/deals")
    public String dealsPage(Model model) {
        log.debug("dealsPage()");

        List<DealSignal> signals = detectDealSignalsUseCase.getRecentSignals(50);
        List<Map<String, Object>> signalList = new ArrayList<>();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();

        for (DealSignal signal : signals) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("signalId", signal.signalId());
            entry.put("title", signal.title());
            entry.put("signalType", signal.signalType().name());
            entry.put("companyName", signal.companyName());
            entry.put("summary", signal.summary());
            entry.put("confidence", String.format("%.0f%%", signal.confidence() * 100));
            entry.put("detectedAt", DISPLAY_FMT.format(signal.detectedAt()));
            signalList.add(entry);

            String typeName = signal.signalType().name();
            typeCounts.put(typeName, typeCounts.getOrDefault(typeName, 0) + 1);
        }

        model.addAttribute("signals", signalList);
        model.addAttribute("typeCounts", typeCounts);
        model.addAttribute("totalSignals", signals.size());
        model.addAttribute("activePage", "deals");

        log.debug("dealsPage() | return=deals ({} signals)", signals.size());
        return "deals";
    }
}

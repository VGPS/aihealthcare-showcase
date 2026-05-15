package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.VendorAssessment;
import com.wgblackmon.aihealthcare.domain.port.inbound.CompareVendorsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collections;
import java.util.List;

/**
 * Thymeleaf controller that renders the vendor comparison UI at {@code GET /research/vendors}.
 *
 * <p>When no query parameter is present the page renders an empty search form.
 * When {@code query} is present the COMBINED research pipeline is executed via
 * {@link CompareVendorsUseCase} and the resulting {@link VendorAssessment} list is
 * displayed in a per-vendor comparison table showing strengths, weaknesses, and a
 * relevance score.
 *
 * <p>Vendor comparison runs are transient — no {@code ResearchRun} record is persisted.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-14
 * @updated 2026-05-14
 */
@Slf4j
@Controller
@RequestMapping("/research/vendors")
public class VendorCompareController {

    private static final int DEFAULT_MAX_SOURCES = 20;
    private static final int MAX_SOURCES_CAP     = 100;

    private final CompareVendorsUseCase compareVendorsUseCase;

    /**
     * Constructs the controller with its vendor-comparison use-case dependency.
     *
     * @param compareVendorsUseCase Inbound port driving the vendor comparison pipeline.
     */
    public VendorCompareController(CompareVendorsUseCase compareVendorsUseCase) {
        log.debug("VendorCompareController() | compareVendorsUseCase={}",
                  compareVendorsUseCase.getClass().getSimpleName());
        this.compareVendorsUseCase = compareVendorsUseCase;
        log.debug("VendorCompareController() | return=void");
    }

    /**
     * Renders the vendor comparison page.
     *
     * <p>When {@code query} is blank the page shows only the search form.
     * When {@code query} is present the COMBINED pipeline runs and results are
     * displayed as a per-vendor comparison table.
     *
     * @param query      Research question entered by the user; optional.
     * @param maxSources Maximum sources per pipeline run; defaults to 20, capped at 100.
     * @param model      Thymeleaf model.
     * @return Thymeleaf view name {@code "vendor-compare"}.
     */
    @GetMapping
    public String compare(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "20") int maxSources,
            Model model) {

        log.debug("compare() | query={}, maxSources={}", query, maxSources);

        int cappedMax = Math.min(Math.max(maxSources, 1), MAX_SOURCES_CAP);
        model.addAttribute("query",      query != null ? query : "");
        model.addAttribute("maxSources", cappedMax);

        if (query == null || query.isBlank()) {
            model.addAttribute("vendors",    Collections.emptyList());
            model.addAttribute("hasResults", false);
            log.debug("compare() | no query — rendering empty form");
            log.debug("compare() | return=vendor-compare");
            return "vendor-compare";
        }

        String trimmed = query.trim();
        List<VendorAssessment> vendors;
        String errorMessage = null;

        try {
            log.info("compare() | running vendor comparison pipeline for query='{}'", trimmed);
            vendors = compareVendorsUseCase.compare(trimmed, cappedMax);
            log.info("compare() | vendor compare complete: vendorCount={}", vendors.size());
        } catch (Exception ex) {
            log.error("compare() | vendor comparison pipeline failed: {}", ex.getMessage());
            vendors      = Collections.emptyList();
            errorMessage = "Vendor comparison pipeline error — check that ANTHROPIC_API_KEY is set. Detail: "
                           + ex.getMessage();
        }

        model.addAttribute("vendors",      vendors);
        model.addAttribute("hasResults",   !vendors.isEmpty());
        model.addAttribute("errorMessage", errorMessage);

        log.debug("compare() | return=vendor-compare");
        return "vendor-compare";
    }
}

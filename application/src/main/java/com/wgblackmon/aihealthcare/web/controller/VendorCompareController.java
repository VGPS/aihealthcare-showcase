package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SourceCitation;
import com.wgblackmon.aihealthcare.domain.model.VendorAssessment;
import com.wgblackmon.aihealthcare.domain.model.VendorCompareResult;
import com.wgblackmon.aihealthcare.domain.port.inbound.CompareVendorsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thymeleaf controller that renders the vendor comparison UI at {@code GET /research/vendors}.
 *
 * <p>When no query parameter is present the page renders an empty search form.
 * When {@code query} is present the COMBINED research pipeline is executed via
 * {@link CompareVendorsUseCase} and the resulting {@link VendorAssessment} list is
 * displayed in a per-vendor comparison table showing strengths, weaknesses, and a
 * relevance score.  The source citations used by the AI are displayed in a numbered
 * reference table between the vendor cards and the methodology note.
 *
 * <p>Vendor comparison runs are transient — no {@code ResearchRun} record is persisted.
 *
 * @author  Bill Blackmon
 * @version 1.3
 * @since   2026-05-14
 * @updated 2026-06-08
 */
@Slf4j
@Controller
@RequestMapping("/research/vendors")
public class VendorCompareController {

    private static final int DEFAULT_MAX_SOURCES = 20;
    private static final int MAX_SOURCES_CAP     = 100;
    private static final int DEFAULT_MIN_VENDORS = 5;
    private static final int MAX_VENDORS_CAP     = 20;

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC);

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
     * displayed as a per-vendor comparison table with a source reference listing.
     *
     * @param query      Research question entered by the user; optional.
     * @param maxSources Maximum sources per pipeline run; defaults to 20, capped at 100.
     * @param minVendors Minimum vendor sections to request; defaults to 5, capped at 20.
     * @param scoring    Scoring algorithm: {@code "TF_IDF"} or {@code "DOC_FREQUENCY"} (default).
     * @param model      Thymeleaf model.
     * @return Thymeleaf view name {@code "vendor-compare"}.
     */
    @GetMapping
    public String compare(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "20") int maxSources,
            @RequestParam(defaultValue = "5") int minVendors,
            @RequestParam(defaultValue = "DOC_FREQUENCY") String scoring,
            Model model) {

        log.debug("compare() | query={}, maxSources={}, minVendors={}, scoring={}",
                  query, maxSources, minVendors, scoring);

        int cappedMax = Math.min(Math.max(maxSources, 1), MAX_SOURCES_CAP);
        int cappedVendors = Math.min(Math.max(minVendors, 1), MAX_VENDORS_CAP);
        model.addAttribute("query",      query != null ? query : "");
        model.addAttribute("maxSources", cappedMax);
        model.addAttribute("minVendors", cappedVendors);
        model.addAttribute("scoring",    scoring);

        if (query == null || query.isBlank()) {
            model.addAttribute("vendors",    Collections.emptyList());
            model.addAttribute("citations",  Collections.emptyList());
            model.addAttribute("hasResults", false);
            log.debug("compare() | no query — rendering empty form");
            log.debug("compare() | return=vendor-compare");
            return "vendor-compare";
        }

        String trimmed = query.trim();
        List<VendorAssessment> vendors;
        List<SourceCitation> citations = Collections.emptyList();
        String errorMessage = null;

        try {
            log.info("compare() | running vendor comparison pipeline for query='{}', scoring={}",
                     trimmed, scoring);
            VendorCompareResult result = compareVendorsUseCase.compare(
                    trimmed, cappedMax, cappedVendors, scoring);
            vendors   = result.vendors();
            citations = result.citations();
            log.info("compare() | vendor compare complete: vendorCount={}, citationCount={}",
                     vendors.size(), citations.size());
        } catch (Exception ex) {
            log.error("compare() | vendor comparison pipeline failed: {}", ex.getMessage());
            vendors      = Collections.emptyList();
            errorMessage = "Vendor comparison pipeline error — check that ANTHROPIC_API_KEY is set. Detail: "
                           + ex.getMessage();
        }

        // Format citation timestamps server-side (Instant is not supported by #temporals)
        Map<Integer, String> citationDates = new HashMap<>();
        for (SourceCitation c : citations) {
            if (c.retrievedAt() != null) {
                citationDates.put(c.citationNumber(), DISPLAY_FMT.format(c.retrievedAt()));
            } else {
                citationDates.put(c.citationNumber(), "—");
            }
        }

        model.addAttribute("vendors",       vendors);
        model.addAttribute("citations",     citations);
        model.addAttribute("citationDates", citationDates);
        model.addAttribute("hasResults",    !vendors.isEmpty());
        model.addAttribute("errorMessage",  errorMessage);

        log.debug("compare() | return=vendor-compare");
        return "vendor-compare";
    }
}

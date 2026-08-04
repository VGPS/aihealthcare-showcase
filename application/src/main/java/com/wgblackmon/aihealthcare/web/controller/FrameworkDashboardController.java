package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.FrameworkDimension;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeFrameworksUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thymeleaf controller that renders the healthcare framework competitive
 * analysis dashboard and detail pages.
 *
 * <p>Serves {@code GET /dashboard/frameworks} showing all analyzed companies
 * with a radar chart comparing dimension scores, and
 * {@code GET /dashboard/frameworks/{slug}} for single-company drill-down.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@Controller
public class FrameworkDashboardController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")
                    .withZone(ZoneId.of("America/New_York"));

    private static final List<String> CHART_COLORS = List.of(
            "#3B82F6", "#EF4444", "#10B981", "#F59E0B", "#8B5CF6", "#EC4899");

    private final AnalyzeFrameworksUseCase frameworksUseCase;
    private final SubscriberPort subscriberPort;

    public FrameworkDashboardController(AnalyzeFrameworksUseCase frameworksUseCase,
                                        SubscriberPort subscriberPort) {
        log.debug("FrameworkDashboardController() | frameworksUseCase={}, subscriberPort={}",
                  frameworksUseCase, subscriberPort);
        this.frameworksUseCase = frameworksUseCase;
        this.subscriberPort = subscriberPort;
    }

    /**
     * Renders the framework competitive analysis overview page with a
     * multi-company radar chart.
     *
     * @param model Thymeleaf model
     * @return the "framework-analysis" view name
     */
    @GetMapping("/dashboard/frameworks")
    public String frameworkOverview(Model model) {
        log.debug("frameworkOverview()");

        List<FrameworkAnalysis> analyses = frameworksUseCase.getAll();

        if (analyses.isEmpty()) {
            model.addAttribute("hasAnalyses", false);
            model.addAttribute("analyses", analyses);
            model.addAttribute("activePage", "frameworks");

            log.debug("frameworkOverview() | return=framework-analysis (empty)");
            return "framework-analysis";
        }

        model.addAttribute("hasAnalyses", true);
        model.addAttribute("analyses", analyses);

        // Company names for chart legend
        List<String> companyNames = new ArrayList<>();
        for (FrameworkAnalysis analysis : analyses) {
            companyNames.add(analysis.companyName());
        }
        model.addAttribute("companyNames", companyNames);

        // Dimension names from the first analysis
        List<String> dimensionNames = new ArrayList<>();
        for (FrameworkDimension dim : analyses.get(0).dimensions()) {
            dimensionNames.add(dim.name());
        }
        model.addAttribute("dimensionNames", dimensionNames);

        // Scores: one inner list per company, each containing that company's dimension scores
        List<List<Integer>> companyScores = new ArrayList<>();
        for (FrameworkAnalysis analysis : analyses) {
            List<Integer> scores = new ArrayList<>();
            for (FrameworkDimension dim : analysis.dimensions()) {
                scores.add(dim.score());
            }
            companyScores.add(scores);
        }
        model.addAttribute("companyScores", companyScores);

        // Chart colors (cycle if more companies than colors)
        List<String> chartColors = new ArrayList<>();
        for (int i = 0; i < analyses.size(); i++) {
            chartColors.add(CHART_COLORS.get(i % CHART_COLORS.size()));
        }
        model.addAttribute("chartColors", chartColors);

        // Formatted analysis dates keyed by slug
        Map<String, String> analyzedDates = new HashMap<>();
        for (FrameworkAnalysis analysis : analyses) {
            analyzedDates.put(analysis.companySlug(), DISPLAY_FMT.format(analysis.analyzedAt()));
        }
        model.addAttribute("analyzedDates", analyzedDates);

        model.addAttribute("activePage", "frameworks");

        log.debug("frameworkOverview() | return=framework-analysis ({} analyses)", analyses.size());
        return "framework-analysis";
    }

    /**
     * Renders the detail page for a single company's framework analysis
     * with an individual radar chart.
     *
     * @param slug  the company's slug identifier
     * @param model Thymeleaf model
     * @return the "framework-detail" view name, or redirect if not found
     */
    @GetMapping("/dashboard/frameworks/{slug}")
    public String frameworkDetail(@PathVariable String slug, Model model) {
        log.debug("frameworkDetail() | slug={}", slug);

        Optional<FrameworkAnalysis> opt = frameworksUseCase.getBySlug(slug);
        if (opt.isEmpty()) {
            log.debug("frameworkDetail() | return=redirect (not found: {})", slug);
            return "redirect:/dashboard/frameworks";
        }

        FrameworkAnalysis analysis = opt.get();

        // Dimension names and scores for single-company radar chart
        List<String> dimNames = new ArrayList<>();
        List<Integer> dimScores = new ArrayList<>();
        for (FrameworkDimension dim : analysis.dimensions()) {
            dimNames.add(dim.name());
            dimScores.add(dim.score());
        }

        // Split assessment into paragraphs for template rendering
        List<String> assessmentParagraphs = new ArrayList<>();
        if (analysis.overallAssessment() != null) {
            String[] paras = analysis.overallAssessment().split("\n\n");
            for (String para : paras) {
                String trimmed = para.trim();
                if (!trimmed.isEmpty()) {
                    assessmentParagraphs.add(trimmed);
                }
            }
        }

        model.addAttribute("analysis", analysis);
        model.addAttribute("dimNames", dimNames);
        model.addAttribute("dimScores", dimScores);
        model.addAttribute("assessmentParagraphs", assessmentParagraphs);
        model.addAttribute("analyzedAt", DISPLAY_FMT.format(analysis.analyzedAt()));
        model.addAttribute("activePage", "frameworks");

        log.debug("frameworkDetail() | return=framework-detail for {}", slug);
        return "framework-detail";
    }
}

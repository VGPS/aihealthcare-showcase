package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AnalystNote;
import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.model.FrameworkDimension;
import com.wgblackmon.aihealthcare.domain.model.NoteTargetType;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeFrameworksUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

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
 * @updated 2026-09-12
 */
@Slf4j
@Controller
public class FrameworkDashboardController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DisplayFormats.TIMESTAMP_24H
                    .withZone(ZoneId.of("America/New_York"));

    // Strips LLM-generated [1], [2], [1,2] citation markers that have no reference list on the page
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[\\d+(?:[,;]\\s*\\d+)*\\]");

    private static final DateTimeFormatter NOTE_FMT =
            DisplayFormats.NOTE_FMT
                    .withZone(ZoneId.of("America/New_York"));

    private static final List<String> CHART_COLORS = List.of(
            "#3B82F6", "#EF4444", "#10B981", "#F59E0B", "#8B5CF6", "#EC4899");

    private static String stripCitations(String text) {
        if (text == null || text.isBlank()) return text;
        String stripped = CITATION_PATTERN.matcher(text).replaceAll("").replaceAll("  +", " ").trim();
        return stripped.isBlank() ? text : stripped;
    }

    private static List<String> stripCitationsList(List<String> items) {
        List<String> result = new ArrayList<>();
        for (String item : items) {
            result.add(stripCitations(item));
        }
        return result;
    }

    private static FrameworkAnalysis cleanAnalysis(FrameworkAnalysis a) {
        List<FrameworkDimension> cleanDims = new ArrayList<>();
        for (FrameworkDimension dim : a.dimensions()) {
            cleanDims.add(new FrameworkDimension(dim.name(), dim.score(), stripCitations(dim.rationale())));
        }
        return new FrameworkAnalysis(
                a.companySlug(), a.companyName(),
                stripCitations(a.overallAssessment()),
                cleanDims,
                stripCitationsList(a.strengths()),
                stripCitationsList(a.weaknesses()),
                stripCitationsList(a.recentDevelopments()),
                a.overallScore(), a.articleCount(), a.analyzedAt());
    }

    private final AnalyzeFrameworksUseCase frameworksUseCase;
    private final SubscriberPort subscriberPort;
    private final AnalystNotePort analystNotePort;
    private final TierResolver tierResolver;

    public FrameworkDashboardController(AnalyzeFrameworksUseCase frameworksUseCase,
                                        SubscriberPort subscriberPort,
                                        AnalystNotePort analystNotePort,
                                        @Nullable TierResolver tierResolver) {
        log.debug("FrameworkDashboardController() | frameworksUseCase={}, subscriberPort={}, analystNotePort={}, tierResolver={}",
                  frameworksUseCase, subscriberPort, analystNotePort, tierResolver);
        this.frameworksUseCase = frameworksUseCase;
        this.subscriberPort = subscriberPort;
        this.analystNotePort = analystNotePort;
        this.tierResolver = tierResolver;
    }

    /**
     * Renders the framework competitive analysis overview page with a
     * multi-company radar chart.
     *
     * @param model Thymeleaf model
     * @return the "framework-analysis" view name
     */
    @GetMapping("/dashboard/frameworks")
    public String frameworkOverview(Model model, Principal principal) {
        log.debug("frameworkOverview() | principal={}", principal != null ? principal.getName() : "anonymous");
        model.addAttribute("isEnterprise", tierResolver != null && tierResolver.hasFullAccess(principal));

        List<FrameworkAnalysis> rawAnalyses = frameworksUseCase.getAll();
        List<FrameworkAnalysis> analyses = new ArrayList<>();
        for (FrameworkAnalysis a : rawAnalyses) {
            analyses.add(cleanAnalysis(a));
        }

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
    public String frameworkDetail(@PathVariable String slug, Model model, Principal principal) {
        log.debug("frameworkDetail() | slug={}, principal={}", slug, principal != null ? principal.getName() : "anonymous");

        if (tierResolver == null || !tierResolver.hasFullAccess(principal)) {
            log.debug("frameworkDetail() | return=framework-detail (upgrade required) for slug={}", slug);
            model.addAttribute("upgradeRequired", true);
            model.addAttribute("activePage", "frameworks");
            return "framework-detail";
        }

        Optional<FrameworkAnalysis> opt = frameworksUseCase.getBySlug(slug);
        if (opt.isEmpty()) {
            log.debug("frameworkDetail() | return=redirect (not found: {})", slug);
            return "redirect:/dashboard/frameworks";
        }

        FrameworkAnalysis analysis = cleanAnalysis(opt.get());
        model.addAttribute("upgradeRequired", false);

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

        // Load analyst notes for this company
        List<AnalystNote> analystNotes = new ArrayList<>();
        Map<String, String> analystNoteDates = new HashMap<>();
        if (principal != null) {
            analystNotes = analystNotePort.findByUserAndTarget(
                    principal.getName(), NoteTargetType.COMPANY, slug);
            for (AnalystNote note : analystNotes) {
                Instant noteTime = note.updatedAt() != null ? note.updatedAt() : note.createdAt();
                analystNoteDates.put(note.noteId(), NOTE_FMT.format(noteTime));
            }
        }
        model.addAttribute("analystNotes", analystNotes);
        model.addAttribute("analystNoteDates", analystNoteDates);
        model.addAttribute("returnUrl", "/dashboard/frameworks/" + slug);

        log.debug("frameworkDetail() | return=framework-detail for {}, noteCount={}", slug, analystNotes.size());
        return "framework-detail";
    }
}

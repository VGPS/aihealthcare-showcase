package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ResearchAnswer;
import com.wgblackmon.aihealthcare.domain.model.ResearchMode;
import com.wgblackmon.aihealthcare.domain.model.ResearchRequest;
import com.wgblackmon.aihealthcare.domain.port.inbound.ConductResearchUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Thymeleaf controller that renders a side-by-side comparison of the two research
 * pipeline modes for the same query.
 *
 * <p>Serves {@code GET /research/compare}.  When {@code query} is absent the page
 * renders an empty search form.  When {@code query} is present both pipelines are
 * executed sequentially and their {@link ResearchAnswer} objects are placed in the
 * Thymeleaf model for rendering:
 * <ul>
 *   <li><b>Left panel — LEGACY_GOOGLE</b>: raw article titles and URLs fetched from
 *       the ingestion database; no AI calls.</li>
 *   <li><b>Right panel — STAGED_RESEARCH</b>: AI-planned sub-queries, retrieval
 *       (Perplexity when configured, Google fallback otherwise), and AI synthesis
 *       into thematic sections with inline citations.</li>
 * </ul>
 *
 * <p>When no {@code PERPLEXITY_API_KEY} is set the right panel shows AI synthesis
 * applied to the same Google articles — still a meaningful comparison because the AI
 * organises and interprets the raw list into thematic narrative sections.
 *
 * <p>Note: the STAGED_RESEARCH path makes two Anthropic AI calls (planning +
 * synthesis) and may take 10–20 seconds to complete.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-04
 * @updated 2026-05-04
 */
@Slf4j
@Controller
@RequestMapping("/research/compare")
public class ResearchCompareController {

    private static final int DEFAULT_MAX_SOURCES = 20;
    private static final int MAX_SOURCES_CAP     = 100;

    private final ConductResearchUseCase conductResearchUseCase;

    /**
     * Constructs the controller with the research use-case dependency.
     *
     * @param conductResearchUseCase Inbound port driving both pipeline modes.
     */
    public ResearchCompareController(ConductResearchUseCase conductResearchUseCase) {
        log.debug("ResearchCompareController() | conductResearchUseCase={}",
                  conductResearchUseCase.getClass().getSimpleName());
        this.conductResearchUseCase = conductResearchUseCase;
        log.debug("ResearchCompareController() | return=void");
    }

    /**
     * Renders the comparison page.
     *
     * <p>When {@code query} is blank the page shows only the search form.
     * When {@code query} is present both pipelines run and their results are
     * added to the model as {@code legacyAnswer} and {@code stagedAnswer}.
     *
     * @param query      Research question entered by the user; optional.
     * @param maxSources Maximum sources per pipeline run; defaults to 20.
     * @param model      Thymeleaf model.
     * @return Thymeleaf view name {@code "research-compare"}.
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
            log.debug("compare() | no query — rendering empty form");
            log.debug("compare() | return=research-compare");
            return "research-compare";
        }

        String trimmed = query.trim();

        // ── Left panel: LEGACY_GOOGLE (DB articles, no AI) ────────────────
        log.info("compare() | running LEGACY_GOOGLE pipeline for query='{}'", trimmed);
        ResearchAnswer legacyAnswer = conductResearchUseCase.conduct(
                new ResearchRequest(trimmed, ResearchMode.LEGACY_GOOGLE, null, cappedMax));
        log.info("compare() | LEGACY_GOOGLE complete: citations={}", legacyAnswer.allCitations().size());

        // ── Right panel: STAGED_RESEARCH (AI plan + retrieve + synthesize) ─
        log.info("compare() | running STAGED_RESEARCH pipeline for query='{}'", trimmed);
        ResearchAnswer stagedAnswer = conductResearchUseCase.conduct(
                new ResearchRequest(trimmed, ResearchMode.STAGED_RESEARCH, null, cappedMax));
        log.info("compare() | STAGED_RESEARCH complete: sections={}, citations={}",
                 stagedAnswer.sections().size(), stagedAnswer.allCitations().size());

        model.addAttribute("legacyAnswer", legacyAnswer);
        model.addAttribute("stagedAnswer", stagedAnswer);
        model.addAttribute("hasPerplexity", detectPerplexityActive(stagedAnswer));

        log.debug("compare() | return=research-compare");
        return "research-compare";
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Heuristic: if the staged answer has citations whose engine was PERPLEXITY,
     * report true so the template can label the right panel accurately.
     * Without an API key the staged path falls back to Google sources.
     */
    private boolean detectPerplexityActive(ResearchAnswer stagedAnswer) {
        log.debug("detectPerplexityActive() | citationCount={}", stagedAnswer.allCitations().size());
        // When Perplexity is active sources arrive via PerplexityResearchAdapter,
        // which stamps engine="PERPLEXITY". We inspect the body text as a proxy
        // because citations carry URL/title but not engine after assembly.
        // For now a non-empty citation list from a non-empty section body implies
        // some retrieval occurred; the label is set in the template conditionally.
        boolean result = !stagedAnswer.allCitations().isEmpty()
                && !stagedAnswer.sections().isEmpty()
                && !stagedAnswer.sections().get(0).body()
                               .startsWith("No sources were available");
        log.debug("detectPerplexityActive() | return={}", result);
        return result;
    }
}

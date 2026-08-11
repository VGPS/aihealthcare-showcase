package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.service.WikiGapAnalysisService;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.NewsArticleRepository;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiGapItemEntity;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiGapRunEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Admin controller for reviewing wiki gap analysis results.
 *
 * <p>Provides a Thymeleaf page at {@code /admin/wiki-gaps} showing recent
 * gap analysis runs, pending gap items with linked article titles, and
 * approve/dismiss actions. Restricted to ADMIN role via SecurityConfig
 * ({@code /admin/**}).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
@Slf4j
@Controller
@RequestMapping("/admin/wiki-gaps")
public class AdminWikiGapController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm z")
                    .withZone(ZoneId.of("America/New_York"));

    private final WikiGapAnalysisService gapService;
    private final NewsArticleRepository articleRepository;

    public AdminWikiGapController(WikiGapAnalysisService gapService,
                                   NewsArticleRepository articleRepository) {
        log.debug("AdminWikiGapController() | gapService={}, articleRepository={}",
                gapService.getClass().getSimpleName(),
                articleRepository.getClass().getSimpleName());
        this.gapService = gapService;
        this.articleRepository = articleRepository;
    }

    /**
     * Renders the wiki gap review page with recent runs and pending items.
     *
     * @param model Thymeleaf model
     * @return the "admin-wiki-gaps" view name
     */
    @GetMapping
    public String gapReview(Model model) {
        log.debug("gapReview()");

        List<WikiGapRunEntity> recentRuns = gapService.getRecentRuns();
        List<WikiGapItemEntity> pendingItems = gapService.getPendingItems();
        List<WikiGapItemEntity> approvedItems = gapService.getApprovedItems();

        Map<Long, String> runTimestamps = new LinkedHashMap<>();
        for (WikiGapRunEntity run : recentRuns) {
            if (run.getStartedAt() != null) {
                runTimestamps.put(run.getId(), DISPLAY_FMT.format(run.getStartedAt()));
            }
        }

        Map<Long, List<Map<String, String>>> itemArticleDetails = new LinkedHashMap<>();
        for (WikiGapItemEntity item : pendingItems) {
            itemArticleDetails.put(item.getId(), resolveArticleTitles(item));
        }
        for (WikiGapItemEntity item : approvedItems) {
            itemArticleDetails.put(item.getId(), resolveArticleTitles(item));
        }

        model.addAttribute("recentRuns", recentRuns);
        model.addAttribute("pendingItems", pendingItems);
        model.addAttribute("approvedItems", approvedItems);
        model.addAttribute("runTimestamps", runTimestamps);
        model.addAttribute("itemArticleDetails", itemArticleDetails);
        model.addAttribute("pendingCount", pendingItems.size());
        model.addAttribute("approvedCount", approvedItems.size());

        log.debug("gapReview() | return=admin-wiki-gaps, runs={}, pending={}, approved={}",
                recentRuns.size(), pendingItems.size(), approvedItems.size());
        return "admin-wiki-gaps";
    }

    /**
     * Renders the detail view for a specific gap analysis run.
     *
     * @param runId the run ID
     * @param model Thymeleaf model
     * @return the "admin-wiki-gaps" view name
     */
    @GetMapping("/{runId}")
    public String runDetail(@PathVariable Long runId, Model model) {
        log.debug("runDetail() | runId={}", runId);

        Optional<WikiGapRunEntity> runOpt = gapService.getRunById(runId);
        if (runOpt.isEmpty()) {
            log.debug("runDetail() | return=redirect (run not found)");
            return "redirect:/admin/wiki-gaps";
        }

        WikiGapRunEntity run = runOpt.get();
        List<WikiGapItemEntity> items = gapService.getItemsByRunId(runId);

        Map<Long, List<Map<String, String>>> itemArticleDetails = new LinkedHashMap<>();
        for (WikiGapItemEntity item : items) {
            itemArticleDetails.put(item.getId(), resolveArticleTitles(item));
        }

        String runTimestamp = run.getStartedAt() != null
                ? DISPLAY_FMT.format(run.getStartedAt()) : "N/A";

        model.addAttribute("selectedRun", run);
        model.addAttribute("selectedRunItems", items);
        model.addAttribute("selectedRunTimestamp", runTimestamp);
        model.addAttribute("itemArticleDetails", itemArticleDetails);

        model.addAttribute("recentRuns", gapService.getRecentRuns());
        model.addAttribute("pendingItems", gapService.getPendingItems());
        model.addAttribute("approvedItems", gapService.getApprovedItems());
        model.addAttribute("runTimestamps", new LinkedHashMap<>());
        model.addAttribute("pendingCount", gapService.getPendingItems().size());
        model.addAttribute("approvedCount", gapService.getApprovedItems().size());

        log.debug("runDetail() | return=admin-wiki-gaps, items={}", items.size());
        return "admin-wiki-gaps";
    }

    /**
     * Approves a gap item for compilation.
     *
     * @param id the gap item ID
     * @param redirectAttributes flash attributes
     * @return redirect to gap review page
     */
    @PostMapping("/items/{id}/approve")
    public String approveItem(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("approveItem() | id={}", id);
        gapService.updateItemStatus(id, "APPROVED");
        redirectAttributes.addFlashAttribute("message", "Gap item approved for compilation");
        log.debug("approveItem() | return=redirect");
        return "redirect:/admin/wiki-gaps";
    }

    /**
     * Dismisses a gap item.
     *
     * @param id the gap item ID
     * @param redirectAttributes flash attributes
     * @return redirect to gap review page
     */
    @PostMapping("/items/{id}/dismiss")
    public String dismissItem(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        log.debug("dismissItem() | id={}", id);
        gapService.updateItemStatus(id, "DISMISSED");
        redirectAttributes.addFlashAttribute("message", "Gap item dismissed");
        log.debug("dismissItem() | return=redirect");
        return "redirect:/admin/wiki-gaps";
    }

    private List<Map<String, String>> resolveArticleTitles(WikiGapItemEntity item) {
        List<Map<String, String>> details = new ArrayList<>();
        List<String> ids = gapService.splitArticleIds(item.getArticleIds());
        if (ids.isEmpty()) {
            return details;
        }

        List<NewsArticleEntity> articles = articleRepository.findByArticleIdIn(ids);
        for (NewsArticleEntity article : articles) {
            Map<String, String> detail = new LinkedHashMap<>();
            detail.put("id", article.getArticleId());
            detail.put("title", article.getTitle());
            detail.put("source", article.getSourceName() != null ? article.getSourceName() : "Unknown");
            details.add(detail);
        }
        return details;
    }
}

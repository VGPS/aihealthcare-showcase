package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.EditorialItem;
import com.wgblackmon.aihealthcare.domain.model.EditorialPriority;
import com.wgblackmon.aihealthcare.domain.model.EditorialStatus;
import com.wgblackmon.aihealthcare.domain.model.SocialPlatform;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageEditorialCalendarUseCase;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageSavedPostsUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Thymeleaf controller for the admin editorial queue at {@code /admin/editorial}.
 *
 * <p>Shows all non-published items filtered and sorted by priority tier,
 * demand signal, and effort. Allows admins to advance an item's lifecycle
 * status one step via a POST action.
 *
 * <p>The public {@code /insights} hub (static HTML articles) is served by
 * {@link InsightsController} and is not affected by this controller.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-04
 */
@Slf4j
@Controller
@RequestMapping("/admin/editorial")
@PreAuthorize("hasRole('ADMIN')")
public class EditorialCalendarController {

    private final ManageEditorialCalendarUseCase editorialUseCase;
    private final ManageSavedPostsUseCase savedPostsUseCase;

    public EditorialCalendarController(ManageEditorialCalendarUseCase editorialUseCase,
                                       ManageSavedPostsUseCase savedPostsUseCase) {
        log.debug("EditorialCalendarController() | editorialUseCase={}, savedPostsUseCase={}",
                editorialUseCase.getClass().getSimpleName(),
                savedPostsUseCase.getClass().getSimpleName());
        this.editorialUseCase = editorialUseCase;
        this.savedPostsUseCase = savedPostsUseCase;
    }

    /**
     * Renders the editorial queue with optional filter params.
     *
     * @param priority optional P0/P1/P2 filter
     * @param signal   optional HOT/STEADY/EVERGREEN filter
     * @param effort   optional S/M/L filter
     * @param model    Spring model
     * @return template name
     */
    @GetMapping
    public String queue(@RequestParam(required = false) String priority,
                        @RequestParam(required = false) String signal,
                        @RequestParam(required = false) String effort,
                        Model model) {
        log.debug("queue() | priority={}, signal={}, effort={}", priority, signal, effort);

        List<EditorialItem> items = editorialUseCase.getQueue();

        if (priority != null && !priority.isBlank()) {
            try {
                EditorialPriority p = EditorialPriority.valueOf(priority.toUpperCase());
                items = items.stream().filter(i -> i.priorityTier() == p).toList();
            } catch (IllegalArgumentException ignored) {}
        }
        if (signal != null && !signal.isBlank()) {
            String sig = signal.toUpperCase();
            items = items.stream()
                    .filter(i -> i.demandSignal() != null && i.demandSignal().name().equals(sig))
                    .toList();
        }
        if (effort != null && !effort.isBlank()) {
            String eff = effort.toUpperCase();
            items = items.stream()
                    .filter(i -> i.effort() != null && i.effort().name().equals(eff))
                    .toList();
        }

        editorialUseCase.getNext().ifPresent(next -> model.addAttribute("nextItem", next));

        model.addAttribute("items", items);
        model.addAttribute("totalCount", items.size());
        model.addAttribute("filterPriority", priority);
        model.addAttribute("filterSignal", signal);
        model.addAttribute("filterEffort", effort);

        log.debug("queue() | return=editorial-queue, items={}", items.size());
        return "editorial-queue";
    }

    /**
     * Advances the status of a single editorial item by one lifecycle step.
     * Redirects back to the queue after the update.
     */
    @PostMapping("/{id}/advance")
    public String advance(@PathVariable String id) {
        log.debug("advance() | id={}", id);
        try {
            EditorialItem updated = editorialUseCase.advanceStatus(id);
            log.debug("advance() | return=redirect, newStatus={}", updated.status());
        } catch (IllegalArgumentException e) {
            log.warn("advance() | item not found: {}", id);
        }
        return "redirect:/admin/editorial";
    }

    /**
     * Creates LinkedIn and Facebook draft posts from an editorial item's hook, title,
     * and CTA, then redirects to the social post draft queue for editing.
     */
    @PostMapping("/{id}/generate-post")
    public String generatePost(@PathVariable String id, RedirectAttributes redirectAttrs) {
        log.debug("generatePost() | id={}", id);
        try {
            EditorialItem item = editorialUseCase.getById(id)
                    .orElseThrow(() -> new IllegalArgumentException("Not found: " + id));

            String cta = item.cta() != null && !item.cta().isBlank() ? "\n\n→ " + item.cta() : "";
            String linkedinBody = item.hook() + "\n\n" + item.title() + cta;
            String facebookBody = item.hook() + "\n\n" + item.title() + cta;
            String dateRef = item.preferredDate().toString();

            savedPostsUseCase.save(SocialPlatform.LINKEDIN, dateRef, linkedinBody, null, "");
            savedPostsUseCase.save(SocialPlatform.FACEBOOK, dateRef, facebookBody, null, "");

            redirectAttrs.addFlashAttribute("successMsg",
                    "LinkedIn and Facebook drafts created from \"" + item.title() + "\"");
            log.debug("generatePost() | return=redirect:/dashboard/social/drafts");
        } catch (IllegalArgumentException e) {
            log.warn("generatePost() | failed: {}", e.getMessage());
            redirectAttrs.addFlashAttribute("errorMsg", "Item not found: " + id);
        }
        return "redirect:/dashboard/social/drafts";
    }
}

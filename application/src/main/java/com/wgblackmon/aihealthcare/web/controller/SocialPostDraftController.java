package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.SavedPost;
import com.wgblackmon.aihealthcare.domain.model.SocialPlatform;
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
import java.util.Optional;

/**
 * Thymeleaf controller for saved social post draft management.
 *
 * <p>Handles saving drafts from the social post generator, editing them via
 * TinyMCE, marking them as posted, and deleting them. All actions are
 * gated to SUBSCRIBER, DEMO, and ADMIN tiers.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@Slf4j
@Controller
@RequestMapping("/dashboard/social/drafts")
public class SocialPostDraftController {

    private final ManageSavedPostsUseCase savedPostsUseCase;
    private final TierResolver tierResolver;

    public SocialPostDraftController(ManageSavedPostsUseCase savedPostsUseCase,
                                     TierResolver tierResolver) {
        log.debug("SocialPostDraftController() | savedPostsUseCase={}", savedPostsUseCase);
        this.savedPostsUseCase = savedPostsUseCase;
        this.tierResolver = tierResolver;
    }

    @GetMapping
    public String listDrafts(Model model, java.security.Principal principal) {
        log.debug("listDrafts() | principal={}", principal != null ? principal.getName() : "anon");
        boolean fullAccess = tierResolver.hasFullAccess(principal);
        model.addAttribute("fullAccess", fullAccess);
        if (fullAccess) {
            List<SavedPost> drafts = savedPostsUseCase.listDrafts();
            model.addAttribute("drafts", drafts);
            model.addAttribute("draftCount", drafts.size());
        }
        log.debug("listDrafts() | return=social-post-drafts");
        return "social-post-drafts";
    }

    @GetMapping("/{postId}/edit")
    public String editDraft(@PathVariable String postId, Model model) {
        log.debug("editDraft() | postId={}", postId);
        Optional<SavedPost> postOpt = savedPostsUseCase.findById(postId);
        if (postOpt.isEmpty()) {
            log.debug("editDraft() | return=redirect:/dashboard/social/drafts (not found)");
            return "redirect:/dashboard/social/drafts";
        }
        model.addAttribute("post", postOpt.get());
        log.debug("editDraft() | return=social-post-draft-edit");
        return "social-post-draft-edit";
    }

    @PostMapping("/{postId}/edit")
    public String saveEdit(@PathVariable String postId,
                           @RequestParam String body,
                           @RequestParam(required = false, defaultValue = "") String comment,
                           @RequestParam(required = false, defaultValue = "") String yourTake,
                           RedirectAttributes redirectAttrs) {
        log.debug("saveEdit() | postId={}, yourTake.length={}", postId, yourTake.length());
        try {
            savedPostsUseCase.update(postId, body, comment.isBlank() ? null : comment, yourTake);
            redirectAttrs.addFlashAttribute("successMsg", "Draft saved.");
        } catch (Exception e) {
            log.error("saveEdit() | failed to save draft postId={}", postId, e);
            redirectAttrs.addFlashAttribute("errorMsg", "Save failed: " + e.getMessage());
        }
        log.debug("saveEdit() | return=redirect:/dashboard/social/drafts/{}/edit", postId);
        return "redirect:/dashboard/social/drafts/" + postId + "/edit";
    }

    @PostMapping("/{postId}/post")
    public String markPosted(@PathVariable String postId, RedirectAttributes redirectAttrs) {
        log.debug("markPosted() | postId={}", postId);
        try {
            savedPostsUseCase.markPosted(postId);
            redirectAttrs.addFlashAttribute("successMsg", "Marked as posted.");
        } catch (Exception e) {
            log.error("markPosted() | failed postId={}", postId, e);
            redirectAttrs.addFlashAttribute("errorMsg", "Failed: " + e.getMessage());
        }
        log.debug("markPosted() | return=redirect:/dashboard/social/drafts");
        return "redirect:/dashboard/social/drafts";
    }

    @PostMapping("/{postId}/delete")
    @PreAuthorize("hasAnyRole('ADMIN','SUBSCRIBER','DEMO')")
    public String deleteDraft(@PathVariable String postId, RedirectAttributes redirectAttrs) {
        log.debug("deleteDraft() | postId={}", postId);
        try {
            savedPostsUseCase.delete(postId);
            redirectAttrs.addFlashAttribute("successMsg", "Draft deleted.");
        } catch (Exception e) {
            log.error("deleteDraft() | failed postId={}", postId, e);
            redirectAttrs.addFlashAttribute("errorMsg", "Delete failed: " + e.getMessage());
        }
        log.debug("deleteDraft() | return=redirect:/dashboard/social/drafts");
        return "redirect:/dashboard/social/drafts";
    }

    @PostMapping("/save-from-generator")
    public String saveFromGenerator(
            @RequestParam String linkedinBody,
            @RequestParam(required = false, defaultValue = "") String linkedinComment,
            @RequestParam String facebookBody,
            @RequestParam(required = false, defaultValue = "") String facebookComment,
            @RequestParam(required = false, defaultValue = "") String yourTake,
            @RequestParam(required = false, defaultValue = "") String digestDate,
            RedirectAttributes redirectAttrs) {
        log.debug("saveFromGenerator() | digestDate={}, yourTake.length={}", digestDate, yourTake.length());
        try {
            savedPostsUseCase.save(SocialPlatform.LINKEDIN, digestDate, linkedinBody,
                    linkedinComment.isBlank() ? null : linkedinComment, yourTake);
            savedPostsUseCase.save(SocialPlatform.FACEBOOK, digestDate, facebookBody,
                    facebookComment.isBlank() ? null : facebookComment, yourTake);
            redirectAttrs.addFlashAttribute("successMsg", "2 drafts saved — LinkedIn and Facebook.");
        } catch (Exception e) {
            log.error("saveFromGenerator() | failed", e);
            redirectAttrs.addFlashAttribute("errorMsg", "Save failed: " + e.getMessage());
        }
        log.debug("saveFromGenerator() | return=redirect:/dashboard/social/drafts");
        return "redirect:/dashboard/social/drafts";
    }
}

package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.AnalystNote;
import com.wgblackmon.aihealthcare.domain.service.LogSanitizer;
import com.wgblackmon.aihealthcare.domain.model.AppUser;
import com.wgblackmon.aihealthcare.domain.model.NoteTargetType;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.outbound.AnalystNotePort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AppUserPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Thymeleaf controller serving the analyst notes page at
 * {@code GET /notes}.
 *
 * <p>Allows SUBSCRIBER, DEMO, and ADMIN users to create, edit, and
 * delete private text notes attached to companies, articles, regulatory
 * events, clinical trials, and wiki pages.
 *
 * <p>FREE and FREE_PENDING users are redirected to the pricing page.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-09-12
 */
@Slf4j
@Controller
public class AnalystNoteController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DisplayFormats.NOTE_FMT
                    .withZone(ZoneId.of("America/New_York"));

    private final AnalystNotePort analystNotePort;
    private final AppUserPort appUserPort;

    public AnalystNoteController(AnalystNotePort analystNotePort,
                                  AppUserPort appUserPort) {
        log.debug("AnalystNoteController() | analystNotePort={}, appUserPort={}",
                analystNotePort.getClass().getSimpleName(),
                appUserPort.getClass().getSimpleName());
        this.analystNotePort = analystNotePort;
        this.appUserPort = appUserPort;
    }

    /**
     * Renders the central notes page with all user notes.
     */
    @GetMapping("/notes")
    public String index(Model model, Principal principal) {
        log.debug("index() | principal={}", principal != null ? principal.getName() : "anonymous");

        String email = principal != null ? principal.getName() : "";

        if (!hasNotesAccess(email)) {
            log.debug("index() | return=redirect:/pricing (tier gate)");
            return "redirect:/pricing";
        }

        List<AnalystNote> notes = analystNotePort.findByUser(email);

        // Group notes by target type for display
        List<AnalystNote> companyNotes = new ArrayList<>();
        List<AnalystNote> articleNotes = new ArrayList<>();
        List<AnalystNote> regulatoryNotes = new ArrayList<>();
        List<AnalystNote> clinicalTrialNotes = new ArrayList<>();
        List<AnalystNote> wikiNotes = new ArrayList<>();

        for (AnalystNote note : notes) {
            if (note.targetType() == NoteTargetType.COMPANY) {
                companyNotes.add(note);
            } else if (note.targetType() == NoteTargetType.ARTICLE) {
                articleNotes.add(note);
            } else if (note.targetType() == NoteTargetType.REGULATORY_EVENT) {
                regulatoryNotes.add(note);
            } else if (note.targetType() == NoteTargetType.CLINICAL_TRIAL) {
                clinicalTrialNotes.add(note);
            } else if (note.targetType() == NoteTargetType.WIKI_PAGE) {
                wikiNotes.add(note);
            }
        }

        // Format dates server-side
        Map<String, String> noteDates = new HashMap<>();
        for (AnalystNote note : notes) {
            Instant displayTime = note.updatedAt() != null ? note.updatedAt() : note.createdAt();
            noteDates.put(note.noteId(), DISPLAY_FMT.format(displayTime));
        }

        model.addAttribute("notes", notes);
        model.addAttribute("companyNotes", companyNotes);
        model.addAttribute("articleNotes", articleNotes);
        model.addAttribute("regulatoryNotes", regulatoryNotes);
        model.addAttribute("clinicalTrialNotes", clinicalTrialNotes);
        model.addAttribute("wikiNotes", wikiNotes);
        model.addAttribute("noteDates", noteDates);
        model.addAttribute("noteCount", notes.size());

        log.debug("index() | return=notes, count={}", notes.size());
        return "notes";
    }

    /**
     * Adds a new analyst note.
     */
    @PostMapping("/notes")
    public String addNote(@RequestParam String targetType,
                          @RequestParam String targetId,
                          @RequestParam String targetLabel,
                          @RequestParam String content,
                          @RequestParam(required = false) String returnUrl,
                          Principal principal,
                          RedirectAttributes redirectAttributes) {
        log.debug("addNote() | targetType={}, targetId={}, targetLabel={}", targetType, targetId, targetLabel);

        String email = principal != null ? principal.getName() : "";

        if (!hasNotesAccess(email)) {
            log.debug("addNote() | return=redirect:/pricing (tier gate)");
            return "redirect:/pricing";
        }

        try {
            NoteTargetType type = NoteTargetType.valueOf(targetType.toUpperCase());
            Instant now = Instant.now();

            AnalystNote note = new AnalystNote(
                    UUID.randomUUID().toString(),
                    email,
                    type,
                    targetId,
                    targetLabel,
                    content,
                    now,
                    now
            );
            analystNotePort.save(note);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Note added to \"" + targetLabel + "\"");
            log.info("addNote() | saved note for targetType={}, targetId={}, user={}", type, targetId, LogSanitizer.maskEmail(email));
        } catch (Exception e) {
            log.error("addNote() | failed to add note", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to add note: " + e.getMessage());
        }

        String redirect = safeReturnUrl(returnUrl);
        log.debug("addNote() | return=redirect:{}", redirect);
        return "redirect:" + redirect;
    }

    /**
     * Edits an existing analyst note's content.
     */
    @PostMapping("/notes/{noteId}/edit")
    public String editNote(@PathVariable String noteId,
                           @RequestParam String content,
                           @RequestParam(required = false) String returnUrl,
                           Principal principal,
                           RedirectAttributes redirectAttributes) {
        log.debug("editNote() | noteId={}", noteId);

        String email = principal != null ? principal.getName() : "";

        if (!hasNotesAccess(email)) {
            log.debug("editNote() | return=redirect:/pricing (tier gate)");
            return "redirect:/pricing";
        }

        Optional<AnalystNote> existing = analystNotePort.findById(noteId);
        if (existing.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Note not found");
            String redirect = safeReturnUrl(returnUrl);
            log.debug("editNote() | return=redirect:{} (not found)", redirect);
            return "redirect:" + redirect;
        }

        AnalystNote old = existing.get();
        if (!old.userEmail().equals(email)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Not authorized to edit this note");
            String redirect = safeReturnUrl(returnUrl);
            log.debug("editNote() | return=redirect:{} (unauthorized)", redirect);
            return "redirect:" + redirect;
        }

        try {
            AnalystNote updated = new AnalystNote(
                    old.noteId(),
                    old.userEmail(),
                    old.targetType(),
                    old.targetId(),
                    old.targetLabel(),
                    content,
                    old.createdAt(),
                    Instant.now()
            );
            analystNotePort.save(updated);
            redirectAttributes.addFlashAttribute("successMessage", "Note updated");
            log.info("editNote() | updated note noteId={}, user={}", noteId, LogSanitizer.maskEmail(email));
        } catch (Exception e) {
            log.error("editNote() | failed to edit note", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Failed to update note: " + e.getMessage());
        }

        String redirect = safeReturnUrl(returnUrl);
        log.debug("editNote() | return=redirect:{}", redirect);
        return "redirect:" + redirect;
    }

    /**
     * Deletes an analyst note.
     */
    @PostMapping("/notes/{noteId}/delete")
    public String deleteNote(@PathVariable String noteId,
                             @RequestParam(required = false) String returnUrl,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        log.debug("deleteNote() | noteId={}", noteId);

        String email = principal != null ? principal.getName() : "";
        analystNotePort.delete(noteId, email);
        redirectAttributes.addFlashAttribute("successMessage", "Note deleted");

        String redirect = safeReturnUrl(returnUrl);
        log.debug("deleteNote() | return=redirect:{}", redirect);
        return "redirect:" + redirect;
    }

    /**
     * Returns true if the user has SUBSCRIBER or DEMO tier (or is ADMIN).
     */
    private boolean hasNotesAccess(String email) {
        Optional<AppUser> userOpt = appUserPort.findByEmail(email);
        if (userOpt.isEmpty()) {
            return false;
        }
        AppUser user = userOpt.get();
        if ("ADMIN".equals(user.role())) {
            return true;
        }
        SubscriptionTier tier = user.tier() != null ? user.tier() : SubscriptionTier.FREE;
        return tier == SubscriptionTier.SUBSCRIBER || tier == SubscriptionTier.DEMO
                || tier == SubscriptionTier.ENTERPRISE;
    }

    /**
     * Validates and returns the return URL, defaulting to /notes.
     * Prevents open redirect by requiring the URL starts with /.
     */
    private String safeReturnUrl(String returnUrl) {
        if (returnUrl != null && !returnUrl.isBlank() && returnUrl.startsWith("/")) {
            return returnUrl;
        }
        return "/notes";
    }
}

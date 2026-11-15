package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.HarmType;
import com.wgblackmon.aihealthcare.domain.model.IncidentStatus;
import com.wgblackmon.aihealthcare.domain.model.IncidentType;
import com.wgblackmon.aihealthcare.domain.model.LiabilityIncident;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.LiabilityIncidentPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin controller for the AI Liability Case Tracker.
 *
 * <p>Provides CRUD endpoints at {@code /admin/liability} for manually seeding
 * and maintaining {@link LiabilityIncident} records. All routes are protected
 * by the existing {@code /admin/**} ADMIN-role rule in SecurityConfig — no
 * additional security configuration is required.
 *
 * <p>Phase 2: each incident row in the list view includes up to 5 related
 * harvested articles matched by the incident's {@code defendant} name, surfaced
 * via {@link LiabilityIncidentPort#findRelatedArticles} at zero LLM cost.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-11-16
 * @updated 2026-11-15
 */
@Slf4j
@Controller
@RequestMapping("/admin/liability")
public class LiabilityTrackerController {

    private static final int RELATED_ARTICLE_LIMIT = 5;

    private final LiabilityIncidentPort port;

    public LiabilityTrackerController(LiabilityIncidentPort port) {
        log.debug("LiabilityTrackerController() | port={}", port.getClass().getSimpleName());
        this.port = port;
    }

    /**
     * List all incidents, split into pending-review queue and published registry.
     */
    @GetMapping
    public String list(Model model) {
        log.debug("list()");
        List<LiabilityIncident> pending   = port.findByStatus(IncidentStatus.PENDING_REVIEW);
        List<LiabilityIncident> incidents = port.findAll().stream()
                .filter(i -> i.status() != IncidentStatus.PENDING_REVIEW)
                .collect(java.util.stream.Collectors.toList());

        Map<String, List<NewsArticle>> relatedByIncident = new LinkedHashMap<>();
        for (LiabilityIncident incident : incidents) {
            List<NewsArticle> related = port.findRelatedArticles(
                    incident.defendant(), RELATED_ARTICLE_LIMIT);
            relatedByIncident.put(incident.incidentId(), related);
        }

        model.addAttribute("pending",            pending);
        model.addAttribute("incidents",          incidents);
        model.addAttribute("relatedByIncident",  relatedByIncident);
        model.addAttribute("incidentTypes",      IncidentType.values());
        model.addAttribute("harmTypes",          HarmType.values());
        model.addAttribute("statuses",           IncidentStatus.values());
        log.debug("list() | return=liability-admin, incidents={}, pending={}", incidents.size(), pending.size());
        return "liability-admin";
    }

    /**
     * Create a new incident from the add form.
     */
    @PostMapping("/add")
    public String add(
            @RequestParam String title,
            @RequestParam String incidentType,
            @RequestParam String harmType,
            @RequestParam String status,
            @RequestParam(required = false) String plaintiff,
            @RequestParam String defendant,
            @RequestParam(required = false) String legalTheory,
            @RequestParam(required = false) String court,
            @RequestParam(required = false) String caseNumber,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate harmDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filingDate,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        log.debug("add() | title={}, incidentType={}, defendant={}", title, incidentType, defendant);
        try {
            Instant now = Instant.now();
            LiabilityIncident incident = new LiabilityIncident(
                    UUID.randomUUID().toString(),
                    IncidentType.valueOf(incidentType),
                    HarmType.valueOf(harmType),
                    IncidentStatus.valueOf(status),
                    title,
                    blankToNull(plaintiff),
                    defendant,
                    blankToNull(legalTheory),
                    blankToNull(court),
                    blankToNull(caseNumber),
                    harmDate,
                    filingDate,
                    blankToNull(outcome),
                    blankToNull(sourceUrl),
                    blankToNull(notes),
                    now,
                    now
            );
            port.save(incident);
            redirectAttributes.addFlashAttribute("successMsg", "Incident added: " + title);
        } catch (Exception e) {
            log.error("add() | failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "Failed to add incident: " + e.getMessage());
        }
        log.debug("add() | return=redirect:/admin/liability");
        return "redirect:/admin/liability";
    }

    /**
     * Show the edit form pre-populated for the given incident.
     */
    @GetMapping("/{incidentId}/edit")
    public String editForm(@PathVariable String incidentId, Model model) {
        log.debug("editForm() | incidentId={}", incidentId);
        LiabilityIncident incident = port.findById(incidentId);
        if (incident == null) {
            log.warn("editForm() | incident not found: {}", incidentId);
            return "redirect:/admin/liability";
        }
        model.addAttribute("incident",      incident);
        model.addAttribute("incidentTypes", IncidentType.values());
        model.addAttribute("harmTypes",     HarmType.values());
        model.addAttribute("statuses",      IncidentStatus.values());
        log.debug("editForm() | return=liability-edit");
        return "liability-edit";
    }

    /**
     * Save edits to an existing incident.
     */
    @PostMapping("/{incidentId}/update")
    public String update(
            @PathVariable String incidentId,
            @RequestParam String title,
            @RequestParam String incidentType,
            @RequestParam String harmType,
            @RequestParam String status,
            @RequestParam(required = false) String plaintiff,
            @RequestParam String defendant,
            @RequestParam(required = false) String legalTheory,
            @RequestParam(required = false) String court,
            @RequestParam(required = false) String caseNumber,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate harmDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate filingDate,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String sourceUrl,
            @RequestParam(required = false) String notes,
            RedirectAttributes redirectAttributes) {

        log.debug("update() | incidentId={}, title={}", incidentId, title);
        try {
            LiabilityIncident existing = port.findById(incidentId);
            if (existing == null) {
                redirectAttributes.addFlashAttribute("errorMsg", "Incident not found: " + incidentId);
                log.debug("update() | return=redirect:/admin/liability (not found)");
                return "redirect:/admin/liability";
            }
            LiabilityIncident updated = new LiabilityIncident(
                    incidentId,
                    IncidentType.valueOf(incidentType),
                    HarmType.valueOf(harmType),
                    IncidentStatus.valueOf(status),
                    title,
                    blankToNull(plaintiff),
                    defendant,
                    blankToNull(legalTheory),
                    blankToNull(court),
                    blankToNull(caseNumber),
                    harmDate,
                    filingDate,
                    blankToNull(outcome),
                    blankToNull(sourceUrl),
                    blankToNull(notes),
                    existing.createdAt(),
                    Instant.now()
            );
            port.save(updated);
            redirectAttributes.addFlashAttribute("successMsg", "Incident updated: " + title);
        } catch (Exception e) {
            log.error("update() | failed: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("errorMsg", "Failed to update: " + e.getMessage());
        }
        log.debug("update() | return=redirect:/admin/liability");
        return "redirect:/admin/liability";
    }

    /**
     * Delete an incident by id.
     */
    @PostMapping("/{incidentId}/delete")
    public String delete(@PathVariable String incidentId, RedirectAttributes redirectAttributes) {
        log.debug("delete() | incidentId={}", incidentId);
        port.deleteById(incidentId);
        redirectAttributes.addFlashAttribute("successMsg", "Incident deleted.");
        log.debug("delete() | return=redirect:/admin/liability");
        return "redirect:/admin/liability";
    }

    /**
     * Approve a PENDING_REVIEW incident — changes status to FILED and publishes it.
     */
    @PostMapping("/{incidentId}/approve")
    public String approve(@PathVariable String incidentId, RedirectAttributes redirectAttributes) {
        log.debug("approve() | incidentId={}", incidentId);
        LiabilityIncident incident = port.findById(incidentId);
        if (incident == null || incident.status() != IncidentStatus.PENDING_REVIEW) {
            redirectAttributes.addFlashAttribute("errorMsg", "Incident not found or not pending review.");
            log.debug("approve() | return=redirect (not found or wrong status)");
            return "redirect:/admin/liability";
        }
        LiabilityIncident approved = new LiabilityIncident(
                incident.incidentId(), incident.incidentType(), incident.harmType(),
                IncidentStatus.FILED,
                incident.title(), incident.plaintiff(), incident.defendant(),
                incident.legalTheory(), incident.court(), incident.caseNumber(),
                incident.harmDate(), incident.filingDate(), incident.outcome(),
                incident.sourceUrl(), incident.notes(),
                incident.createdAt(), Instant.now()
        );
        port.save(approved);
        redirectAttributes.addFlashAttribute("successMsg", "Incident approved: " + incident.title());
        log.debug("approve() | return=redirect:/admin/liability");
        return "redirect:/admin/liability";
    }

    /**
     * Reject a PENDING_REVIEW incident — deletes it permanently.
     */
    @PostMapping("/{incidentId}/reject")
    public String reject(@PathVariable String incidentId, RedirectAttributes redirectAttributes) {
        log.debug("reject() | incidentId={}", incidentId);
        LiabilityIncident incident = port.findById(incidentId);
        if (incident == null || incident.status() != IncidentStatus.PENDING_REVIEW) {
            redirectAttributes.addFlashAttribute("errorMsg", "Incident not found or not pending review.");
            log.debug("reject() | return=redirect (not found or wrong status)");
            return "redirect:/admin/liability";
        }
        port.deleteById(incidentId);
        redirectAttributes.addFlashAttribute("successMsg", "Incident rejected and removed.");
        log.debug("reject() | return=redirect:/admin/liability");
        return "redirect:/admin/liability";
    }

    // -------------------------------------------------------------------------

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}

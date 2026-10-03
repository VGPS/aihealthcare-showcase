package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanyContact;
import com.wgblackmon.aihealthcare.domain.model.CompanyOutreach;
import com.wgblackmon.aihealthcare.domain.model.ContactSource;
import com.wgblackmon.aihealthcare.domain.model.ContactStatus;
import com.wgblackmon.aihealthcare.domain.model.OutreachPurpose;
import com.wgblackmon.aihealthcare.domain.model.OutreachStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageOutreachUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Admin controller for the Company Outreach CRM.
 *
 * <p>All endpoints require the ADMIN role (enforced by {@code SecurityConfig}
 * via the {@code /admin/**} pattern). Provides list, company detail, and
 * CRUD actions for outreach records and individual contacts.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
@Slf4j
@Controller
@RequestMapping("/admin/outreach")
public class OutreachController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.of("America/Chicago"));

    private final ManageOutreachUseCase outreachUseCase;

    public OutreachController(ManageOutreachUseCase outreachUseCase) {
        log.debug("OutreachController() | outreachUseCase={}", outreachUseCase.getClass().getSimpleName());
        this.outreachUseCase = outreachUseCase;
    }

    /** List all outreach records grouped by slug. */
    @GetMapping
    public String listOutreach(Model model) {
        log.debug("listOutreach()");
        List<CompanyOutreach> allOutreach = outreachUseCase.listAllOutreach();
        model.addAttribute("allOutreach", allOutreach);
        model.addAttribute("purposes", OutreachPurpose.values());
        model.addAttribute("statuses", OutreachStatus.values());
        model.addAttribute("displayFmt", DISPLAY_FMT);
        log.debug("listOutreach() | return=view:outreach");
        return "outreach";
    }

    /** Add a new outreach record for a company. */
    @PostMapping("/add")
    public String addOutreach(@RequestParam String slug,
                              @RequestParam OutreachPurpose purpose,
                              @RequestParam(required = false) String notes,
                              RedirectAttributes flash) {
        log.debug("addOutreach() | slug={}, purpose={}", slug, purpose);
        try {
            outreachUseCase.addOutreach(slug.trim().toLowerCase(), purpose, notes);
            flash.addFlashAttribute("successMsg", "Outreach record added for " + slug);
        } catch (Exception ex) {
            log.warn("addOutreach() | error: {}", ex.getMessage());
            flash.addFlashAttribute("errorMsg", ex.getMessage());
        }
        log.debug("addOutreach() | return=redirect:/admin/outreach");
        return "redirect:/admin/outreach";
    }

    /** Company detail page — outreach rows + contacts. */
    @GetMapping("/{slug}")
    public String companyDetail(@PathVariable String slug, Model model) {
        log.debug("companyDetail() | slug={}", slug);
        List<CompanyOutreach> outreachRows = outreachUseCase.listOutreachBySlug(slug);
        List<CompanyContact> contacts = outreachUseCase.listContacts(slug);
        model.addAttribute("slug", slug);
        model.addAttribute("outreachRows", outreachRows);
        model.addAttribute("contacts", contacts);
        model.addAttribute("purposes", OutreachPurpose.values());
        model.addAttribute("outreachStatuses", OutreachStatus.values());
        model.addAttribute("contactStatuses", ContactStatus.values());
        model.addAttribute("contactSources", ContactSource.values());
        model.addAttribute("displayFmt", DISPLAY_FMT);
        log.debug("companyDetail() | return=view:outreach-company");
        return "outreach-company";
    }

    /** Update outreach status. */
    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam OutreachStatus status,
                               @RequestParam String slug,
                               RedirectAttributes flash) {
        log.debug("updateStatus() | id={}, status={}", id, status);
        try {
            outreachUseCase.updateOutreachStatus(id, status);
            flash.addFlashAttribute("successMsg", "Status updated to " + status);
        } catch (Exception ex) {
            log.warn("updateStatus() | error: {}", ex.getMessage());
            flash.addFlashAttribute("errorMsg", ex.getMessage());
        }
        log.debug("updateStatus() | return=redirect:/admin/outreach/{}", slug);
        return "redirect:/admin/outreach/" + slug;
    }

    /** Update outreach notes. */
    @PostMapping("/{id}/notes")
    public String updateNotes(@PathVariable Long id,
                              @RequestParam String notes,
                              @RequestParam String slug,
                              RedirectAttributes flash) {
        log.debug("updateNotes() | id={}", id);
        try {
            outreachUseCase.updateOutreachNotes(id, notes);
            flash.addFlashAttribute("successMsg", "Notes saved");
        } catch (Exception ex) {
            log.warn("updateNotes() | error: {}", ex.getMessage());
            flash.addFlashAttribute("errorMsg", ex.getMessage());
        }
        log.debug("updateNotes() | return=redirect:/admin/outreach/{}", slug);
        return "redirect:/admin/outreach/" + slug;
    }

    /** Delete an outreach record. */
    @PostMapping("/{id}/delete")
    public String deleteOutreach(@PathVariable Long id,
                                 @RequestParam String slug,
                                 RedirectAttributes flash) {
        log.debug("deleteOutreach() | id={}, slug={}", id, slug);
        outreachUseCase.deleteOutreach(id);
        flash.addFlashAttribute("successMsg", "Outreach record deleted");
        log.debug("deleteOutreach() | return=redirect:/admin/outreach");
        return "redirect:/admin/outreach";
    }

    /** Add a contact to a company. */
    @PostMapping("/{slug}/contacts/add")
    public String addContact(@PathVariable String slug,
                             @RequestParam String fullName,
                             @RequestParam(required = false) String jobTitle,
                             @RequestParam(required = false) String email,
                             @RequestParam(required = false) String linkedinUrl,
                             @RequestParam ContactSource source,
                             @RequestParam(required = false) String notes,
                             RedirectAttributes flash) {
        log.debug("addContact() | slug={}, fullName={}, source={}", slug, fullName, source);
        try {
            outreachUseCase.addContact(slug, fullName, jobTitle, email, linkedinUrl, source, notes);
            flash.addFlashAttribute("successMsg", "Contact " + fullName + " added");
        } catch (Exception ex) {
            log.warn("addContact() | error: {}", ex.getMessage());
            flash.addFlashAttribute("errorMsg", ex.getMessage());
        }
        log.debug("addContact() | return=redirect:/admin/outreach/{}", slug);
        return "redirect:/admin/outreach/" + slug;
    }

    /** Update contact status. */
    @PostMapping("/contacts/{contactId}/status")
    public String updateContactStatus(@PathVariable Long contactId,
                                      @RequestParam ContactStatus status,
                                      @RequestParam String slug,
                                      RedirectAttributes flash) {
        log.debug("updateContactStatus() | contactId={}, status={}", contactId, status);
        try {
            outreachUseCase.updateContactStatus(contactId, status);
            flash.addFlashAttribute("successMsg", "Contact status updated");
        } catch (Exception ex) {
            log.warn("updateContactStatus() | error: {}", ex.getMessage());
            flash.addFlashAttribute("errorMsg", ex.getMessage());
        }
        log.debug("updateContactStatus() | return=redirect:/admin/outreach/{}", slug);
        return "redirect:/admin/outreach/" + slug;
    }

    /** Update contact email. */
    @PostMapping("/contacts/{contactId}/email")
    public String updateContactEmail(@PathVariable Long contactId,
                                     @RequestParam String email,
                                     @RequestParam String slug,
                                     RedirectAttributes flash) {
        log.debug("updateContactEmail() | contactId={}", contactId);
        try {
            outreachUseCase.updateContactEmail(contactId, email);
            flash.addFlashAttribute("successMsg", "Email updated");
        } catch (Exception ex) {
            log.warn("updateContactEmail() | error: {}", ex.getMessage());
            flash.addFlashAttribute("errorMsg", ex.getMessage());
        }
        log.debug("updateContactEmail() | return=redirect:/admin/outreach/{}", slug);
        return "redirect:/admin/outreach/" + slug;
    }

    /** Delete a contact. */
    @PostMapping("/contacts/{contactId}/delete")
    public String deleteContact(@PathVariable Long contactId,
                                @RequestParam String slug,
                                RedirectAttributes flash) {
        log.debug("deleteContact() | contactId={}, slug={}", contactId, slug);
        outreachUseCase.deleteContact(contactId);
        flash.addFlashAttribute("successMsg", "Contact deleted");
        log.debug("deleteContact() | return=redirect:/admin/outreach/{}", slug);
        return "redirect:/admin/outreach/" + slug;
    }
}

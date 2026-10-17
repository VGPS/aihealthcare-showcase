package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanyContact;
import com.wgblackmon.aihealthcare.domain.model.CompanyOutreach;
import com.wgblackmon.aihealthcare.domain.model.ContactSource;
import com.wgblackmon.aihealthcare.domain.model.ContactStatus;
import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.model.OutreachPurpose;
import com.wgblackmon.aihealthcare.domain.model.OutreachStatus;
import com.wgblackmon.aihealthcare.domain.port.inbound.ManageOutreachUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

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

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MM/dd/yyyy").withZone(ZoneId.of("America/Chicago"));

    private final ManageOutreachUseCase outreachUseCase;
    private final HealthcareAiCompanyPort companyPort;

    public OutreachController(ManageOutreachUseCase outreachUseCase,
                              HealthcareAiCompanyPort companyPort) {
        log.debug("OutreachController() | outreachUseCase={}, companyPort={}",
                outreachUseCase.getClass().getSimpleName(), companyPort.getClass().getSimpleName());
        this.outreachUseCase = outreachUseCase;
        this.companyPort = companyPort;
    }

    /** List all outreach records. Supports sort and dir params; default updatedAt DESC. */
    @GetMapping
    public String listOutreach(
            @RequestParam(required = false, defaultValue = "updatedAt") String sort,
            @RequestParam(required = false, defaultValue = "desc") String dir,
            Model model) {
        log.debug("listOutreach() | sort={}, dir={}", sort, dir);
        List<CompanyOutreach> allOutreach = outreachUseCase.listAllOutreach();
        List<HealthcareAiCompany> companies = companyPort.findAllByOrderByName();

        // In-memory sort
        String effectiveSort = sort;
        Comparator<CompanyOutreach> cmp;
        switch (sort) {
            case "slug":
                cmp = Comparator.comparing(CompanyOutreach::slug,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "purpose":
                cmp = Comparator.comparing((CompanyOutreach r) -> r.purpose().name());
                break;
            case "status":
                cmp = Comparator.comparing((CompanyOutreach r) -> r.status().name());
                break;
            case "contactedAt":
                cmp = Comparator.comparing(CompanyOutreach::contactedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "linkedinSentAt":
                cmp = Comparator.comparing(CompanyOutreach::linkedinSentAt,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            case "linkedinOutcome":
                cmp = Comparator.comparing(
                        (CompanyOutreach r) -> r.linkedinOutcome() != null ? r.linkedinOutcome() : "");
                break;
            default:
                effectiveSort = "updatedAt";
                cmp = Comparator.comparing(CompanyOutreach::updatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder()));
                break;
        }
        if ("desc".equals(dir)) {
            cmp = cmp.reversed();
        }
        List<CompanyOutreach> sorted = new ArrayList<>(allOutreach);
        sorted.sort(cmp);

        // Build name→id map for JS combo box lookup
        Map<String, String> companyNameToId = new LinkedHashMap<>();
        for (HealthcareAiCompany c : companies) {
            companyNameToId.put(c.name(), c.companyId());
        }
        model.addAttribute("allOutreach", sorted);
        model.addAttribute("companies", companies);
        model.addAttribute("companyNameToId", companyNameToId);
        model.addAttribute("purposes", OutreachPurpose.values());
        model.addAttribute("statuses", OutreachStatus.values());
        model.addAttribute("displayFmt", DISPLAY_FMT);
        model.addAttribute("dateFmt", DATE_FMT);
        model.addAttribute("sortField", effectiveSort);
        model.addAttribute("sortDir", dir);
        log.debug("listOutreach() | return=view:outreach, records={}, sort={}, dir={}",
                sorted.size(), effectiveSort, dir);
        return "outreach";
    }

    /** Exports all outreach records as a CSV file, sorted by updatedAt DESC. */
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv() {
        log.debug("exportCsv()");
        List<CompanyOutreach> all = outreachUseCase.listAllOutreach();
        all = new ArrayList<>(all);
        all.sort(Comparator.comparing(CompanyOutreach::updatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())).reversed());

        StringBuilder csv = new StringBuilder();
        csv.append("Company Slug,Purpose,Status,Contacted At,LinkedIn Sent At,LinkedIn Outcome,Notes,Last Updated\n");
        for (CompanyOutreach row : all) {
            csv.append(escapeCsv(row.slug())).append(",");
            csv.append(row.purpose().name()).append(",");
            csv.append(row.status().name()).append(",");
            csv.append(row.contactedAt() != null ? DATE_FMT.format(row.contactedAt()) : "").append(",");
            csv.append(row.linkedinSentAt() != null ? DATE_FMT.format(row.linkedinSentAt()) : "").append(",");
            csv.append(escapeCsv(row.linkedinOutcome())).append(",");
            csv.append(escapeCsv(row.notes())).append(",");
            csv.append(row.updatedAt() != null ? DATE_FMT.format(row.updatedAt()) : "").append("\n");
        }

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"outreach.csv\"");
        log.debug("exportCsv() | return={} rows", all.size());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private static String escapeCsv(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * JSON endpoint — returns existing contacts for a slug.
     * Used by the add-form combo box to show contacts below when a company is selected.
     * Literal path takes precedence over the /{slug} pattern.
     */
    @GetMapping(value = "/contacts-json", produces = "application/json")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> contactsJson(@RequestParam String slug) {
        log.debug("contactsJson() | slug={}", slug);
        List<CompanyContact> contacts = outreachUseCase.listContacts(slug);
        List<Map<String, Object>> result = contacts.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id());
            m.put("fullName", c.fullName());
            m.put("jobTitle", c.jobTitle() != null ? c.jobTitle() : "");
            m.put("email", c.email() != null ? c.email() : "");
            m.put("linkedinUrl", c.linkedinUrl() != null ? c.linkedinUrl() : "");
            m.put("source", c.source().name());
            m.put("status", c.status().name());
            return m;
        }).toList();
        log.debug("contactsJson() | return=size:{}", result.size());
        return ResponseEntity.ok(result);
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
        java.util.Optional<HealthcareAiCompany> found = companyPort.findBySlug(slug);
        String companyName = found.map(HealthcareAiCompany::name).orElse(slug);
        model.addAttribute("slug", slug);
        model.addAttribute("companyName", companyName);
        model.addAttribute("companyFound", found.isPresent());
        model.addAttribute("outreachRows", outreachRows);
        model.addAttribute("contacts", contacts);
        model.addAttribute("purposes", OutreachPurpose.values());
        model.addAttribute("outreachStatuses", OutreachStatus.values());
        model.addAttribute("contactStatuses", ContactStatus.values());
        model.addAttribute("contactSources", ContactSource.values());
        model.addAttribute("displayFmt", DISPLAY_FMT);
        log.debug("companyDetail() | return=view:outreach-company, companyFound={}", found.isPresent());
        return "outreach-company";
    }

    /** Register a company in the directory from the outreach detail page. */
    @PostMapping("/{slug}/register-company")
    public String registerCompany(@PathVariable String slug,
                                  @RequestParam String name,
                                  @RequestParam(required = false) String domain,
                                  @RequestParam(required = false) String description,
                                  RedirectAttributes flash) {
        log.debug("registerCompany() | slug={}, name={}", slug, name);
        try {
            String nameNormalized = name.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
            HealthcareAiCompany company = new HealthcareAiCompany(
                    slug, name.trim(), nameNormalized,
                    (domain != null && !domain.isBlank()) ? domain.trim() : null,
                    (description != null && !description.isBlank()) ? description.trim() : null,
                    null, null, null, null, null, null, null, null,
                    java.util.List.of(), false, java.util.List.of(),
                    Instant.now(), null);
            companyPort.save(company);
            flash.addFlashAttribute("successMsg", name + " added to the company directory");
        } catch (Exception ex) {
            log.warn("registerCompany() | error: {}", ex.getMessage());
            flash.addFlashAttribute("errorMsg", "Could not register company: " + ex.getMessage());
        }
        log.debug("registerCompany() | return=redirect:/admin/outreach/{}", slug);
        return "redirect:/admin/outreach/" + slug;
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

    /** Update LinkedIn sent date and outcome for an outreach row. */
    @PostMapping("/{id}/linkedin")
    public String updateLinkedIn(@PathVariable Long id,
                                 @RequestParam(required = false) String linkedinSentAt,
                                 @RequestParam(required = false) String linkedinOutcome,
                                 @RequestParam String slug,
                                 RedirectAttributes flash) {
        log.debug("updateLinkedIn() | id={}, linkedinSentAt={}, linkedinOutcome={}", id, linkedinSentAt, linkedinOutcome);
        try {
            Instant sentAt = (linkedinSentAt != null && !linkedinSentAt.isBlank())
                    ? Instant.parse(linkedinSentAt + "T00:00:00Z") : null;
            String outcome = (linkedinOutcome != null && !linkedinOutcome.isBlank()) ? linkedinOutcome : null;
            outreachUseCase.updateLinkedIn(id, sentAt, outcome);
            flash.addFlashAttribute("successMsg", "LinkedIn details saved");
        } catch (Exception ex) {
            log.warn("updateLinkedIn() | error: {}", ex.getMessage());
            flash.addFlashAttribute("errorMsg", ex.getMessage());
        }
        log.debug("updateLinkedIn() | return=redirect:/admin/outreach/{}", slug);
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

package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.port.inbound.BrowseCompaniesUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public-facing Thymeleaf controller for the AI healthcare company directory.
 *
 * <p>Serves {@code GET /companies} (company list) and
 * {@code GET /companies/{slug}} (company detail). Both routes are
 * {@code permitAll()} in Spring Security — no login required.
 *
 * <p>Companies are sourced from the existing {@code healthcare_ai_companies}
 * table populated by the Perplexity discovery pipeline. The directory is
 * read-only and sorted alphabetically. Sector filtering is supported via a
 * {@code sector} query parameter on the list endpoint.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-26
 * @updated 2026-08-26
 */
@Slf4j
@Controller
@RequestMapping("/directory")
public class PublicCompanyController {

    private final BrowseCompaniesUseCase browseCompaniesUseCase;

    public PublicCompanyController(BrowseCompaniesUseCase browseCompaniesUseCase) {
        log.debug("PublicCompanyController() | browseCompaniesUseCase={}",
                  browseCompaniesUseCase.getClass().getSimpleName());
        this.browseCompaniesUseCase = browseCompaniesUseCase;
    }

    /**
     * Renders the public company directory listing.
     *
     * @param sector optional sector filter
     * @param model  Thymeleaf model
     * @return the "company-directory" view name
     */
    @GetMapping
    public String directory(
            @RequestParam(required = false) String sector,
            Model model) {
        log.debug("directory() | sector={}", sector);

        List<HealthcareAiCompany> all = browseCompaniesUseCase.listCompanies();

        // Apply optional category filter
        List<HealthcareAiCompany> companies;
        if (sector != null && !sector.isBlank()) {
            companies = new ArrayList<>();
            for (HealthcareAiCompany c : all) {
                if (sector.equalsIgnoreCase(c.category())) {
                    companies.add(c);
                }
            }
        } else {
            companies = all;
        }

        // Build slug map for template link generation (companyId → slug)
        Map<String, String> slugs = new HashMap<>();
        for (HealthcareAiCompany c : all) {
            slugs.put(c.companyId(), toSlug(c.name()));
        }

        // Collect distinct canonical categories for filter pills
        List<String> sectors = new ArrayList<>();
        for (HealthcareAiCompany c : all) {
            if (c.category() != null && !c.category().isBlank() && !sectors.contains(c.category())) {
                sectors.add(c.category());
            }
        }

        model.addAttribute("companies", companies);
        model.addAttribute("slugs", slugs);
        model.addAttribute("sectors", sectors);
        model.addAttribute("selectedSector", sector);
        model.addAttribute("totalCount", all.size());

        log.debug("directory() | return=company-directory, shown={}, total={}", companies.size(), all.size());
        return "company-directory";
    }

    /**
     * Renders the public company detail page.
     *
     * @param slug  URL slug for the company (e.g. "grelin-health")
     * @param model Thymeleaf model
     * @return "company-detail" view, or redirect to directory if not found
     */
    @GetMapping("/{slug}")
    public String detail(@PathVariable String slug, Model model) {
        log.debug("detail() | slug={}", slug);

        Optional<HealthcareAiCompany> found = browseCompaniesUseCase.getCompany(slug);
        if (found.isEmpty()) {
            log.debug("detail() | return=redirect:/directory (not found)");
            return "redirect:/directory";
        }

        HealthcareAiCompany c = found.get();
        model.addAttribute("company", c);
        model.addAttribute("slug", slug);
        model.addAttribute("jsonLd", buildJsonLd(c));
        model.addAttribute("descriptionHtml", buildDescriptionHtml(c.description()));

        log.debug("detail() | return=company-directory-detail, name={}", c.name());
        return "company-directory-detail";
    }

    /** Converts a display name to a URL-safe slug (e.g. "Grelin Health" → "grelin-health"). */
    static String toSlug(String name) {
        if (name == null || name.isBlank()) return "";
        return name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    /** Converts [N] citation markers in description text to anchor links targeting #source-N. */
    static String buildDescriptionHtml(String description) {
        if (description == null || description.isBlank()) return null;
        String escaped = description
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return escaped.replaceAll("\\[(\\d+)\\]",
                "<a href=\"#source-$1\" class=\"text-primary-600 hover:underline font-medium\">[$1]</a>");
    }

    private static String buildJsonLd(HealthcareAiCompany c) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"@context\":\"https://schema.org\",");
        sb.append("\"@type\":\"Organization\",");
        sb.append("\"name\":\"").append(escapeJson(c.name())).append("\"");
        if (c.description() != null && !c.description().isBlank()) {
            sb.append(",\"description\":\"").append(escapeJson(c.description())).append("\"");
        }
        if (c.domain() != null && !c.domain().isBlank()) {
            sb.append(",\"url\":\"https://").append(c.domain()).append("\"");
        }
        if (c.foundedYear() != null) {
            sb.append(",\"foundingDate\":\"").append(c.foundedYear()).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}

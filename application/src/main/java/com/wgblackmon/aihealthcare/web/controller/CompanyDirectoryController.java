package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.port.outbound.HealthcareAiCompanyPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thymeleaf controller rendering the AI Healthcare Company Directory page
 * at {@code GET /dashboard/companies}.
 *
 * <p>Displays all companies discovered via the Perplexity-powered pipeline
 * with sorting, sub-sector filtering, and validation badges.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-02
 * @updated 2026-08-02
 */
@Slf4j
@Controller
public class CompanyDirectoryController {

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy")
                    .withZone(ZoneId.of("America/New_York"));

    private final HealthcareAiCompanyPort companyPort;

    public CompanyDirectoryController(HealthcareAiCompanyPort companyPort) {
        log.debug("CompanyDirectoryController() | companyPort={}", companyPort.getClass().getSimpleName());
        this.companyPort = companyPort;
    }

    /**
     * Renders the company directory page.
     *
     * @param filter optional sub-sector filter (e.g. "diagnostics", "clinical documentation")
     * @param sort   optional sort column (default "name")
     * @param model  Thymeleaf model
     * @return the "companies" view name
     */
    @GetMapping("/dashboard/companies")
    public String companies(@RequestParam(required = false) String filter,
                            @RequestParam(required = false, defaultValue = "name") String sort,
                            Model model) {
        log.debug("companies() | filter={}, sort={}", filter, sort);

        List<HealthcareAiCompany> companies = companyPort.findAll();

        // Filter by sub-sector
        if (filter != null && !filter.isBlank()) {
            List<HealthcareAiCompany> filtered = new ArrayList<>();
            for (HealthcareAiCompany c : companies) {
                if (c.subSector() != null && c.subSector().equalsIgnoreCase(filter)) {
                    filtered.add(c);
                }
            }
            companies = filtered;
        }

        // Sort
        companies = sortCompanies(companies, sort);

        // Collect unique sub-sectors for filter tabs
        List<String> subSectors = new ArrayList<>();
        for (HealthcareAiCompany c : companyPort.findAll()) {
            if (c.subSector() != null && !c.subSector().isBlank()) {
                boolean exists = false;
                for (String existing : subSectors) {
                    if (existing.equalsIgnoreCase(c.subSector())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    subSectors.add(c.subSector());
                }
            }
        }
        subSectors.sort(String.CASE_INSENSITIVE_ORDER);

        // Format dates
        Map<String, String> discoveredDates = new HashMap<>();
        Map<String, String> validatedDates = new HashMap<>();
        for (HealthcareAiCompany c : companies) {
            discoveredDates.put(c.companyId(), DISPLAY_FMT.format(c.discoveredAt()));
            if (c.lastValidatedAt() != null) {
                validatedDates.put(c.companyId(), DISPLAY_FMT.format(c.lastValidatedAt()));
            }
        }

        // Count validated
        int validatedCount = 0;
        for (HealthcareAiCompany c : companies) {
            if (c.validated()) {
                validatedCount++;
            }
        }

        model.addAttribute("companies", companies);
        model.addAttribute("companyCount", companies.size());
        model.addAttribute("validatedCount", validatedCount);
        model.addAttribute("subSectors", subSectors);
        model.addAttribute("filter", filter);
        model.addAttribute("sort", sort);
        model.addAttribute("discoveredDates", discoveredDates);
        model.addAttribute("validatedDates", validatedDates);

        log.debug("companies() | return=companies, count={}", companies.size());
        return "companies";
    }

    /**
     * Sorts the company list by the specified column.
     */
    private List<HealthcareAiCompany> sortCompanies(List<HealthcareAiCompany> companies, String sort) {
        log.debug("sortCompanies() | sort={}, size={}", sort, companies.size());
        if (companies.isEmpty()) {
            log.debug("sortCompanies() | return=empty list");
            return companies;
        }

        boolean descending = sort != null && sort.endsWith("_desc");
        String column = descending ? sort.substring(0, sort.length() - 5) : sort;

        Comparator<HealthcareAiCompany> comparator;
        if ("sector".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    c -> c.subSector() != null ? c.subSector() : "",
                    String.CASE_INSENSITIVE_ORDER);
        } else if ("funding".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    c -> c.fundingStage() != null ? c.fundingStage() : "",
                    String.CASE_INSENSITIVE_ORDER);
        } else if ("location".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    c -> c.hqLocation() != null ? c.hqLocation() : "",
                    String.CASE_INSENSITIVE_ORDER);
        } else if ("discovered".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(HealthcareAiCompany::discoveredAt);
        } else if ("founded".equalsIgnoreCase(column)) {
            comparator = Comparator.comparing(
                    c -> c.foundedYear() != null ? c.foundedYear() : 0);
        } else {
            // Default: name
            comparator = Comparator.comparing(HealthcareAiCompany::name, String.CASE_INSENSITIVE_ORDER);
        }

        if (descending) {
            comparator = comparator.reversed();
        }

        List<HealthcareAiCompany> sorted = new ArrayList<>(companies);
        sorted.sort(comparator);
        log.debug("sortCompanies() | return=sorted list, size={}", sorted.size());
        return sorted;
    }
}

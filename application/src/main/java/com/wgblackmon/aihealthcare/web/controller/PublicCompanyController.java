package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanySignal;
import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.port.inbound.BrowseCompaniesUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Public-facing Thymeleaf controller for the AI healthcare company directory.
 *
 * <p>Serves {@code GET /directory} (company list with sort/filter/export) and
 * {@code GET /directory/{slug}} (company detail). Both routes are
 * {@code permitAll()} in Spring Security — no login required.
 *
 * <p>The directory supports four views driven by a {@code sort} parameter:
 * <ul>
 *   <li><b>relevance</b> (default) — composite score (article velocity + deal bonus − sentiment penalty)</li>
 *   <li><b>trending</b> — companies with 3+ article mentions in the last 90 days, highest first</li>
 *   <li><b>funded</b> — companies with a detected FUNDING signal, most recent deal first</li>
 *   <li><b>watchlist</b> — companies with negative sentiment score (risk flags), most negative first</li>
 * </ul>
 *
 * <p>A {@code GET /directory/export.csv} endpoint streams all companies with their
 * signal data as a downloadable CSV file.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-26
 * @updated 2026-08-27
 */
@Slf4j
@Controller
@RequestMapping("/directory")
public class PublicCompanyController {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final BrowseCompaniesUseCase browseCompaniesUseCase;

    public PublicCompanyController(BrowseCompaniesUseCase browseCompaniesUseCase) {
        log.debug("PublicCompanyController() | browseCompaniesUseCase={}",
                  browseCompaniesUseCase.getClass().getSimpleName());
        this.browseCompaniesUseCase = browseCompaniesUseCase;
    }

    /**
     * Renders the public company directory listing.
     *
     * @param sector optional canonical category filter (e.g. "Medical Imaging & Diagnostics")
     * @param sort   optional sort mode: "trending", "funded", "watchlist", or null (relevance)
     * @param model  Thymeleaf model
     * @return the "company-directory" view name
     */
    @GetMapping
    public String directory(
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String sort,
            Model model) {
        log.debug("directory() | sector={}, sort={}", sector, sort);

        List<HealthcareAiCompany> all = browseCompaniesUseCase.listCompanies();
        Map<String, CompanySignal> signals = browseCompaniesUseCase.computeSignals(all);

        // Apply sector filter
        List<HealthcareAiCompany> filtered;
        if (sector != null && !sector.isBlank()) {
            filtered = new ArrayList<>();
            for (HealthcareAiCompany c : all) {
                if (sector.equalsIgnoreCase(c.category())) {
                    filtered.add(c);
                }
            }
        } else {
            filtered = new ArrayList<>(all);
        }

        // Apply sort mode and optional view filter
        List<HealthcareAiCompany> companies = applySortAndFilter(filtered, signals, sort);

        // Build slug map for template link generation
        Map<String, String> slugs = new HashMap<>();
        for (HealthcareAiCompany c : all) {
            slugs.put(c.companyId(), toSlug(c.name()));
        }

        // Collect distinct canonical categories for sector pills
        List<String> sectors = new ArrayList<>();
        for (HealthcareAiCompany c : all) {
            if (c.category() != null && !c.category().isBlank() && !sectors.contains(c.category())) {
                sectors.add(c.category());
            }
        }

        // Counts for sort tab badges
        int trendingCount = 0;
        int fundedCount = 0;
        int watchlistCount = 0;
        for (HealthcareAiCompany c : all) {
            CompanySignal sig = signals.get(c.companyId());
            if (sig == null) continue;
            if (sig.isTrending()) trendingCount++;
            if (sig.hasRecentFunding()) fundedCount++;
            if (sig.isWatchList()) watchlistCount++;
        }

        model.addAttribute("companies", companies);
        model.addAttribute("signals", signals);
        model.addAttribute("slugs", slugs);
        model.addAttribute("sectors", sectors);
        model.addAttribute("selectedSector", sector);
        model.addAttribute("selectedSort", sort);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("trendingCount", trendingCount);
        model.addAttribute("fundedCount", fundedCount);
        model.addAttribute("watchlistCount", watchlistCount);

        log.debug("directory() | return=company-directory, shown={}, total={}", companies.size(), all.size());
        return "company-directory";
    }

    /**
     * Streams all companies and their signals as a CSV file download.
     */
    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv() {
        log.debug("exportCsv() |");

        List<HealthcareAiCompany> all = browseCompaniesUseCase.listCompanies();
        Map<String, CompanySignal> signals = browseCompaniesUseCase.computeSignals(all);

        StringBuilder csv = new StringBuilder();
        csv.append("Name,Category,HQ,Founded,Funding Stage,Articles (90d),Has Funding Signal,Deal Amount,Sentiment,Sentiment Score,Relevance Score\n");
        for (HealthcareAiCompany c : all) {
            CompanySignal sig = signals.getOrDefault(c.companyId(),
                    new CompanySignal(c.companyId(), 0, null, null, null, 0.0, null, false, 0));
            csv.append(escapeCsv(c.name())).append(",");
            csv.append(escapeCsv(c.category())).append(",");
            csv.append(escapeCsv(c.hqLocation())).append(",");
            csv.append(c.foundedYear() != null ? c.foundedYear() : "").append(",");
            csv.append(escapeCsv(c.fundingStage())).append(",");
            csv.append(sig.articleCount90d()).append(",");
            csv.append(sig.hasRecentFunding() ? "Yes" : "No").append(",");
            csv.append(escapeCsv(sig.latestDealAmount())).append(",");
            csv.append(sig.sentimentLabel() != null ? sig.sentimentLabel() : "").append(",");
            csv.append(sig.hasSentimentData() ? String.format("%.2f", sig.sentimentScore()) : "").append(",");
            csv.append(sig.relevanceScore()).append("\n");
        }

        byte[] bytes = csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ai-healthcare-companies.csv\"");

        log.debug("exportCsv() | return={} rows", all.size());
        return ResponseEntity.ok().headers(headers).body(bytes);
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

    // ── Sorting ──────────────────────────────────────────────────────────────

    private List<HealthcareAiCompany> applySortAndFilter(List<HealthcareAiCompany> companies,
                                                          Map<String, CompanySignal> signals,
                                                          String sort) {
        if ("trending".equals(sort)) {
            List<HealthcareAiCompany> result = new ArrayList<>(companies);
            result.sort(Comparator.comparingInt(
                    (HealthcareAiCompany c) -> signalOf(c, signals).articleCount90d()).reversed());
            return result;
        }
        if ("funded".equals(sort)) {
            List<HealthcareAiCompany> result = new ArrayList<>();
            for (HealthcareAiCompany c : companies) {
                if (signalOf(c, signals).hasRecentFunding()) result.add(c);
            }
            result.sort(Comparator.comparing(
                    (HealthcareAiCompany c) -> signalOf(c, signals).latestDealDate(),
                    Comparator.nullsLast(Comparator.reverseOrder())));
            return result;
        }
        if ("watchlist".equals(sort)) {
            List<HealthcareAiCompany> result = new ArrayList<>();
            for (HealthcareAiCompany c : companies) {
                if (signalOf(c, signals).isWatchList()) result.add(c);
            }
            result.sort(Comparator.comparingDouble(
                    (HealthcareAiCompany c) -> signalOf(c, signals).sentimentScore()));
            return result;
        }
        // Default: relevance score DESC
        List<HealthcareAiCompany> result = new ArrayList<>(companies);
        result.sort(Comparator.comparingInt(
                (HealthcareAiCompany c) -> signalOf(c, signals).relevanceScore()).reversed());
        return result;
    }

    private CompanySignal signalOf(HealthcareAiCompany c, Map<String, CompanySignal> signals) {
        CompanySignal sig = signals.get(c.companyId());
        return sig != null ? sig : new CompanySignal(c.companyId(), 0, null, null, null, 0.0, null, false, 0);
    }

    // ── Static utilities ──────────────────────────────────────────────────────

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

    private static String escapeCsv(String s) {
        if (s == null || s.isBlank()) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
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

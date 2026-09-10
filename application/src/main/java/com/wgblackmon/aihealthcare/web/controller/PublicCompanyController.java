package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanySignal;
import com.wgblackmon.aihealthcare.domain.service.SlugUtils;
import com.wgblackmon.aihealthcare.domain.model.HealthcareAiCompany;
import com.wgblackmon.aihealthcare.domain.model.Subscriber;
import com.wgblackmon.aihealthcare.domain.model.SubscriptionTier;
import com.wgblackmon.aihealthcare.domain.port.inbound.BrowseCompaniesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.SubscriberPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
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
 * <p>Sort-tab access is tiered:
 * <ul>
 *   <li><b>Public</b> — Relevance (default) view; basic article-count badge on cards</li>
 *   <li><b>FREE+</b> — Trending tab (article velocity sort)</li>
 *   <li><b>SUBSCRIBER / DEMO</b> — Recently Funded + Watch List tabs</li>
 *   <li><b>ENTERPRISE</b> — CSV export at {@code GET /directory/export.csv}</li>
 *   <li><b>ADMIN</b> — all of the above</li>
 * </ul>
 *
 * <p>Server-side enforcement: if a lower-tier user manually constructs a gated sort URL,
 * the controller falls back to the relevance view and sets {@code upgradeRequired=true}
 * so the template can display an upgrade prompt.
 *
 * @author  Bill Blackmon
 * @version 1.2
 * @since   2026-08-26
 * @updated 2026-08-27
 */
@Slf4j
@Controller
@RequestMapping("/directory")
public class PublicCompanyController {

    private final BrowseCompaniesUseCase browseCompaniesUseCase;
    private final SubscriberPort subscriberPort;

    public PublicCompanyController(BrowseCompaniesUseCase browseCompaniesUseCase,
                                    SubscriberPort subscriberPort) {
        log.debug("PublicCompanyController() | browseCompaniesUseCase={}, subscriberPort={}",
                  browseCompaniesUseCase.getClass().getSimpleName(),
                  subscriberPort.getClass().getSimpleName());
        this.browseCompaniesUseCase = browseCompaniesUseCase;
        this.subscriberPort = subscriberPort;
    }

    /**
     * Renders the public company directory listing.
     *
     * @param sector    optional canonical category filter
     * @param sort      optional sort mode: "trending", "funded", "watchlist", or null (relevance)
     * @param principal authenticated user, or null for anonymous visitors
     * @param model     Thymeleaf model
     * @return the "company-directory" view name
     */
    @GetMapping
    public String directory(
            @RequestParam(required = false) String sector,
            @RequestParam(required = false) String sort,
            Principal principal,
            Model model) {
        log.debug("directory() | sector={}, sort={}, user={}", sector, sort,
                  principal != null ? principal.getName() : "anonymous");

        boolean canTrending     = canUseTrending(principal);
        boolean canAdvancedSort = canUseAdvancedSort(principal);
        boolean canExport       = canExport(principal);

        // Gate sort server-side — downgrade and flag for upgrade prompt
        boolean upgradeRequired = false;
        String effectiveSort = sort;
        if ("trending".equals(sort) && !canTrending) {
            effectiveSort = null;
            upgradeRequired = true;
        } else if (("funded".equals(sort) || "watchlist".equals(sort)) && !canAdvancedSort) {
            effectiveSort = null;
            upgradeRequired = true;
        }

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

        List<HealthcareAiCompany> companies = applySortAndFilter(filtered, signals, effectiveSort);

        Map<String, String> slugs = new HashMap<>();
        for (HealthcareAiCompany c : all) {
            slugs.put(c.companyId(), toSlug(c.name()));
        }

        List<String> sectors = new ArrayList<>();
        for (HealthcareAiCompany c : all) {
            if (c.category() != null && !c.category().isBlank() && !sectors.contains(c.category())) {
                sectors.add(c.category());
            }
        }

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
        model.addAttribute("selectedSort", effectiveSort);
        model.addAttribute("totalCount", all.size());
        model.addAttribute("trendingCount", trendingCount);
        model.addAttribute("fundedCount", fundedCount);
        model.addAttribute("watchlistCount", watchlistCount);
        model.addAttribute("canTrending", canTrending);
        model.addAttribute("canAdvancedSort", canAdvancedSort);
        model.addAttribute("canExport", canExport);
        model.addAttribute("upgradeRequired", upgradeRequired);
        model.addAttribute("pageDescription",
                "Browse " + all.size() + " AI healthcare companies with signal scoring, acquisition tracking, and sector classification.");

        log.debug("directory() | return=company-directory, shown={}, total={}", companies.size(), all.size());
        return "company-directory";
    }

    /**
     * Streams all companies and their signals as a CSV file download.
     * Requires ENTERPRISE tier or ADMIN role — others are redirected to /pricing.
     */
    @GetMapping("/export.csv")
    public Object exportCsv(Principal principal) {
        log.debug("exportCsv() | user={}", principal != null ? principal.getName() : "anonymous");

        if (!canExport(principal)) {
            log.debug("exportCsv() | return=redirect:/pricing (tier insufficient)");
            return "redirect:/pricing";
        }

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
        model.addAttribute("pageDescription",
                c.name() + " — AI healthcare company profile: " + (c.category() != null ? c.category() : "healthcare AI") + ".");

        log.debug("detail() | return=company-directory-detail, name={}", c.name());
        return "company-directory-detail";
    }

    // ── Tier checks ───────────────────────────────────────────────────────────

    /** FREE+ (any authenticated user) can use the Trending sort tab. */
    private boolean canUseTrending(Principal principal) {
        return principal != null;
    }

    /** SUBSCRIBER / DEMO / ENTERPRISE / ADMIN can use Funded and Watch List tabs. */
    private boolean canUseAdvancedSort(Principal principal) {
        if (principal == null) return false;
        if (isAdmin(principal)) return true;
        Optional<Subscriber> sub = subscriberPort.findByEmail(principal.getName());
        if (sub.isEmpty()) return false;
        SubscriptionTier tier = sub.get().tier();
        return tier == SubscriptionTier.SUBSCRIBER || tier == SubscriptionTier.DEMO
                || tier == SubscriptionTier.ENTERPRISE;
    }

    /** Only ENTERPRISE tier and ADMIN can download the CSV export. */
    private boolean canExport(Principal principal) {
        if (principal == null) return false;
        if (isAdmin(principal)) return true;
        Optional<Subscriber> sub = subscriberPort.findByEmail(principal.getName());
        if (sub.isEmpty()) return false;
        return sub.get().tier() == SubscriptionTier.ENTERPRISE;
    }

    private boolean isAdmin(Principal principal) {
        if (principal instanceof Authentication) {
            Authentication auth = (Authentication) principal;
            for (GrantedAuthority authority : auth.getAuthorities()) {
                if ("ROLE_ADMIN".equals(authority.getAuthority())) return true;
            }
        }
        return false;
    }

    // ── Sorting ───────────────────────────────────────────────────────────────

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
        return SlugUtils.toSlug(name);
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

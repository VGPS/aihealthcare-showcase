package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Company;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java domain service that renders a list of classified companies
 * into newsletter-ready markdown grouped by AI-healthcare subcategory.
 *
 * <p>Only companies with both {@code isAI} and {@code isHealth} flags
 * are included. Within each section, non-anchor companies appear first
 * (alphabetically), followed by anchors (alphabetically). Sections with
 * no matching companies are omitted.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-07
 * @updated 2026-06-07
 */
public class CompanyNewsletterRenderer {

    /**
     * Generates newsletter markdown from the given companies.
     *
     * @param companies classified, deduped companies
     * @return markdown string with sections grouped by tag category
     */
    public String renderMarkdown(List<Company> companies) {
        // Filter to AI + Health only
        List<Company> filtered = new ArrayList<>();
        for (Company c : companies) {
            if (c.isAI() && c.isHealth()) {
                filtered.add(c);
            }
        }

        if (filtered.isEmpty()) {
            return "# New AI Healthcare Companies\n\n_No AI healthcare companies discovered in this run._\n";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# New AI Healthcare Companies\n\n");

        appendSection(sb, "## New AI Scribes & Ambient Documentation", filtered, "scribe");
        appendSection(sb, "## AI Agents & Virtual Care", filtered, "agent");
        appendSection(sb, "## Imaging & Diagnostics AI", filtered, "imaging");
        appendSection(sb, "## RCM, Coding & Billing Automation", filtered, "rcm");
        appendSection(sb, "## Healthcare AI Infrastructure & Data Platforms", filtered, "infra");

        // Uncategorized — companies with no specific tag but still AI+Health
        List<Company> uncategorized = new ArrayList<>();
        for (Company c : filtered) {
            if (!c.tags().scribe() && !c.tags().agent() && !c.tags().imaging()
                    && !c.tags().rcm() && !c.tags().infra()) {
                uncategorized.add(c);
            }
        }
        if (!uncategorized.isEmpty()) {
            sb.append("## Other AI Healthcare Companies\n\n");
            appendCompanyList(sb, uncategorized);
        }

        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String heading, List<Company> all, String tagName) {
        List<Company> matching = new ArrayList<>();
        for (Company c : all) {
            if (hasTag(c, tagName)) {
                matching.add(c);
            }
        }
        if (matching.isEmpty()) {
            return;
        }

        sb.append(heading).append("\n\n");
        appendCompanyList(sb, matching);
    }

    private void appendCompanyList(StringBuilder sb, List<Company> companies) {
        // Separate non-anchor and anchor, sort each alphabetically
        List<Company> nonAnchor = new ArrayList<>();
        List<Company> anchor = new ArrayList<>();

        for (Company c : companies) {
            if ("anchor".equals(c.source())) {
                anchor.add(c);
            } else {
                nonAnchor.add(c);
            }
        }

        nonAnchor.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        anchor.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));

        for (Company c : nonAnchor) {
            sb.append(formatCompany(c));
        }

        if (!anchor.isEmpty()) {
            sb.append("\n_Established players:_\n\n");
            for (Company c : anchor) {
                sb.append(formatCompany(c));
            }
        }

        sb.append("\n");
    }

    private String formatCompany(Company c) {
        StringBuilder line = new StringBuilder();
        line.append("- **").append(c.name()).append("**");

        if (c.description() != null && !c.description().isBlank()) {
            line.append(" – ").append(c.description());
        }

        line.append("  \n  ");

        boolean hasWebsite = c.companySite() != null && !c.companySite().isBlank();
        boolean hasProfile = c.url() != null && !c.url().isBlank();

        if (hasWebsite) {
            line.append("[Website](").append(c.companySite()).append(")");
            if (hasProfile) {
                line.append(" · ");
            }
        }
        if (hasProfile) {
            line.append("[Profile](").append(c.url()).append(")");
        }

        line.append("\n");
        return line.toString();
    }

    private boolean hasTag(Company c, String tagName) {
        switch (tagName) {
            case "scribe": return c.tags().scribe();
            case "agent": return c.tags().agent();
            case "imaging": return c.tags().imaging();
            case "rcm": return c.tags().rcm();
            case "infra": return c.tags().infra();
            default: return false;
        }
    }
}

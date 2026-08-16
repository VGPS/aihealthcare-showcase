package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEventType;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.port.inbound.MonitorRegulatoryEventsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure domain service that builds a "Legal &amp; Regulatory Brief" newsletter
 * section from recent legal articles and regulatory events.
 *
 * <p>Merges three data sources — litigation articles ("AI Healthcare Legal"),
 * policy articles ("AI Healthcare Government Policy"), and regulatory events
 * (FDA/CMS) — into a single structured section. Like
 * {@link ReversalWatchSectionBuilder}, this is template-based (no LLM call)
 * for deterministic, zero-cost output.
 *
 * <p>Returns {@code null} when no legal data exists in the lookback window,
 * signaling the caller to omit the section entirely.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-30
 * @updated 2026-07-30
 */
@Slf4j
public class LegalBriefSectionBuilder {

    private static final String TOPIC_LEGAL = "AI Healthcare Legal";
    private static final String TOPIC_POLICY = "AI Healthcare Government Policy";
    private static final int MAX_HEADLINES = 5;
    private static final int MAX_REG_EVENTS = 10;

    private final ArticleIngestionPort articleIngestionPort;
    private final MonitorRegulatoryEventsUseCase regulatoryUseCase;

    public LegalBriefSectionBuilder(ArticleIngestionPort articleIngestionPort,
                                     MonitorRegulatoryEventsUseCase regulatoryUseCase) {
        log.debug("LegalBriefSectionBuilder() | articleIngestionPort={}, regulatoryUseCase={}",
                  articleIngestionPort, regulatoryUseCase);
        this.articleIngestionPort = articleIngestionPort;
        this.regulatoryUseCase = regulatoryUseCase;
    }

    /**
     * Builds a LEGAL_BRIEF newsletter section from recent legal/policy articles
     * and regulatory events.
     *
     * @param sectionId    unique section identifier (e.g. "section-006")
     * @param lookbackDays number of days to look back for articles
     * @return a {@link NewsletterSection} summarizing legal developments,
     *         or {@code null} if no data exists in the window
     */
    public NewsletterSection build(String sectionId, int lookbackDays) {
        log.debug("build() | sectionId={}, lookbackDays={}", sectionId, lookbackDays);

        List<NewsArticle> legalArticles = articleIngestionPort
                .fetchByTopicWithArchiveLimit(TOPIC_LEGAL, lookbackDays);
        List<NewsArticle> policyArticles = articleIngestionPort
                .fetchByTopicWithArchiveLimit(TOPIC_POLICY, lookbackDays);
        List<RegulatoryEvent> regEvents = regulatoryUseCase.getRecentEvents(MAX_REG_EVENTS);

        int litigationCount = legalArticles.size();
        int policyCount = policyArticles.size();
        int regulatoryCount = regEvents.size();
        int total = litigationCount + policyCount + regulatoryCount;

        if (total == 0) {
            log.debug("build() | return=null (no legal data in window)");
            return null;
        }

        // Build headline
        String headline = buildHeadline(litigationCount, policyCount, regulatoryCount);

        // Build summary as newline-separated lines with ##-prefixed category sub-headers.
        // NewsletterRenderer detects ## markers to render bold headings + bullet lists.
        StringBuilder summary = new StringBuilder();

        if (!legalArticles.isEmpty()) {
            summary.append("##Litigation (").append(litigationCount).append(")");
            for (int i = 0; i < legalArticles.size() && i < MAX_HEADLINES; i++) {
                summary.append("\n").append(buildArticleItem(legalArticles.get(i)));
            }
        }

        if (!policyArticles.isEmpty()) {
            if (summary.length() > 0) summary.append("\n");
            summary.append("##Policy (").append(policyCount).append(")");
            for (int i = 0; i < policyArticles.size() && i < MAX_HEADLINES; i++) {
                summary.append("\n").append(buildArticleItem(policyArticles.get(i)));
            }
        }

        if (!regEvents.isEmpty()) {
            if (summary.length() > 0) summary.append("\n");
            summary.append("##Regulatory Events (").append(regulatoryCount).append(")");
            for (int i = 0; i < regEvents.size() && i < 3; i++) {
                summary.append("\n").append(buildRegItem(regEvents.get(i)));
            }
        }

        // Collect article IDs for attribution
        List<String> articleIds = new ArrayList<>();
        for (NewsArticle article : legalArticles) {
            articleIds.add(article.articleId());
        }
        for (NewsArticle article : policyArticles) {
            articleIds.add(article.articleId());
        }
        // Add regulatory event IDs as well
        for (RegulatoryEvent event : regEvents) {
            articleIds.add(event.eventId());
        }

        // Fallback if somehow all IDs are missing
        if (articleIds.isEmpty()) {
            articleIds.add("legal-brief-summary");
        }

        NewsletterSection result = new NewsletterSection(
                sectionId,
                SectionType.LEGAL_BRIEF,
                "Legal & Regulatory Brief",
                headline,
                summary.toString(),
                articleIds
        );

        log.debug("build() | return={}", result);
        return result;
    }

    private String buildHeadline(int litigationCount, int policyCount, int regulatoryCount) {
        List<String> parts = new ArrayList<>();
        if (litigationCount > 0) {
            parts.add(litigationCount + " litigation update" + (litigationCount == 1 ? "" : "s"));
        }
        if (policyCount > 0) {
            parts.add(policyCount + " policy development" + (policyCount == 1 ? "" : "s"));
        }
        if (regulatoryCount > 0) {
            parts.add(regulatoryCount + " regulatory event" + (regulatoryCount == 1 ? "" : "s"));
        }

        StringBuilder headline = new StringBuilder("Legal Brief: ");
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0 && i == parts.size() - 1) {
                headline.append(" and ");
            } else if (i > 0) {
                headline.append(", ");
            }
            headline.append(parts.get(i));
        }

        return headline.toString();
    }

    /**
     * Formats an article as "displayTitle||url" for link rendering.
     * Uses sourceName when the article title looks like a bare domain.
     */
    private String buildArticleItem(NewsArticle article) {
        String displayTitle;
        if (isDomainTitle(article.title())) {
            String src = article.sourceName();
            displayTitle = (src != null && !src.isBlank()) ? src : article.title();
        } else {
            displayTitle = truncateTitle(article.title());
        }
        String url = article.url() != null ? article.url().toString() : "";
        return url.isBlank() ? displayTitle : displayTitle + "||" + url;
    }

    /**
     * Formats a regulatory event as "TypeLabel: Title (Applicant)||sourceUrl".
     */
    private String buildRegItem(RegulatoryEvent event) {
        StringBuilder label = new StringBuilder(formatEventType(event.eventType()));
        label.append(": ").append(truncateTitle(event.title()));
        if (event.applicantName() != null && !event.applicantName().isBlank()) {
            label.append(" (").append(event.applicantName()).append(")");
        }
        String url = event.sourceUrl() != null ? event.sourceUrl() : "";
        return url.isBlank() ? label.toString() : label + "||" + url;
    }

    private String formatEventType(RegulatoryEventType type) {
        if (type == null) return "Regulatory";
        switch (type) {
            case FDA_510K_CLEARANCE:              return "FDA 510(k)";
            case FDA_DE_NOVO_CLASSIFICATION:      return "FDA De Novo";
            case FDA_PMA_APPROVAL:                return "FDA PMA";
            case FDA_GUIDANCE_DRAFT:              return "FDA Guidance (Draft)";
            case FDA_GUIDANCE_FINAL:              return "FDA Guidance";
            case CMS_PROPOSED_RULE:               return "CMS Proposed Rule";
            case CMS_FINAL_RULE:                  return "CMS Final Rule";
            case CMS_NCD:                         return "CMS Coverage Decision";
            case FDA_DIGITAL_HEALTH_ANNOUNCEMENT: return "FDA Digital Health";
            default:                              return "Regulatory";
        }
    }

    /** Returns true when a title is a bare domain name rather than an article headline. */
    private boolean isDomainTitle(String title) {
        if (title == null) return false;
        // Strip em-dash or hyphen publisher suffix to get the raw title part
        String base = title.split("\\s[—\\-]\\s")[0].trim();
        // Matches hostnames like "pmc.ncbi.nlm.nih.gov" (lower-case, dots, optional path)
        return base.matches("[a-z0-9][a-z0-9.\\-]*\\.[a-z]{2,}(/[\\S]*)?");
    }

    private String truncateTitle(String title) {
        if (title == null) return "";
        // Strip publisher suffix from Google News titles ("Headline - Publisher")
        int dashIdx = title.lastIndexOf(" - ");
        if (dashIdx > 0 && dashIdx < title.length() - 3) {
            title = title.substring(0, dashIdx).trim();
        }
        if (title.length() > 150) {
            return title.substring(0, 147) + "...";
        }
        return title;
    }
}

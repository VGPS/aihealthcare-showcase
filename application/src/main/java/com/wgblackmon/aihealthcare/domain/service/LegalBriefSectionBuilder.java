package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.RegulatoryEvent;
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
                summary.append("\n").append(truncateTitle(legalArticles.get(i).title()));
            }
        }

        if (!policyArticles.isEmpty()) {
            if (summary.length() > 0) summary.append("\n");
            summary.append("##Policy (").append(policyCount).append(")");
            for (int i = 0; i < policyArticles.size() && i < MAX_HEADLINES; i++) {
                summary.append("\n").append(truncateTitle(policyArticles.get(i).title()));
            }
        }

        if (!regEvents.isEmpty()) {
            if (summary.length() > 0) summary.append("\n");
            summary.append("##Regulatory (").append(regulatoryCount).append(")");
            for (int i = 0; i < regEvents.size() && i < 3; i++) {
                RegulatoryEvent event = regEvents.get(i);
                summary.append("\n[").append(event.eventType().name()).append("] ");
                summary.append(truncateTitle(event.title()));
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

    private String truncateTitle(String title) {
        if (title == null) {
            return "";
        }
        // Strip publisher suffix from Google News titles ("Headline - Publisher")
        int dashIdx = title.lastIndexOf(" - ");
        if (dashIdx > 0 && dashIdx < title.length() - 3) {
            title = title.substring(0, dashIdx).trim();
        }
        if (title.length() > 80) {
            return title.substring(0, 77) + "...";
        }
        return title;
    }
}

package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure domain predicate that decides whether a {@link NewsArticle} carries
 * enough metadata to be worth rendering or persisting.
 *
 * <p>Articles produced by harvesters that cannot extract a real headline
 * (e.g. Perplexity API stubs, malformed RSS items) arrive with their source
 * name as the title and no body text.  Showing "PERPLEXITY" as a clickable
 * newsletter headline is worse than showing nothing — this filter removes them
 * before they reach email rendering or the database.
 *
 * <p>Rules: an article is <em>unusable</em> when any of these hold:
 * <ul>
 *   <li>title is null or blank</li>
 *   <li>title (trimmed, uppercased) is in the known source-label set</li>
 *   <li>title equals sourceName (case-insensitive) — harvester used source name as fallback</li>
 *   <li>title is shorter than 10 chars AND bodyText is null/blank</li>
 *   <li>title is a bare domain/URL pattern AND sourceName is a known label — the newsletter
 *       renderer would display the source label ("PERPLEXITY") as the headline, which is useless</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-08-23
 * @updated 2026-08-23
 */
public class ArticleQualityFilter {

    /**
     * Source / feed-tier names that are never valid article headlines.
     * When a harvester cannot extract a title it falls back to the source
     * label — catching those labels here keeps junk out of the newsletter.
     */
    private static final Set<String> LABEL_TITLES;

    static {
        Set<String> labels = new HashSet<>();
        labels.add("PERPLEXITY");
        labels.add("RESEARCH");
        labels.add("ACADEMIC");
        labels.add("INDUSTRY");
        labels.add("REGULATORY");
        labels.add("COMPETITOR");
        labels.add("HUGGINGFACE");
        labels.add("PUBMED");
        LABEL_TITLES = labels;
    }

    /**
     * Returns {@code true} when the article has enough metadata to be
     * rendered in a newsletter or persisted to the database.
     *
     * @param article the article to evaluate
     * @return {@code true} if usable, {@code false} to discard
     */
    public boolean isUsable(NewsArticle article) {
        String title = article.title();
        if (title == null || title.isBlank()) {
            return false;
        }

        String t = title.trim();

        if (LABEL_TITLES.contains(t.toUpperCase())) {
            return false;
        }

        String sourceName = article.sourceName();
        if (sourceName != null && !sourceName.isBlank()
                && t.equalsIgnoreCase(sourceName.trim())) {
            return false;
        }

        if (t.length() < 10
                && (article.bodyText() == null || article.bodyText().isBlank())) {
            return false;
        }

        // Domain-URL title (e.g. "who.int — news") + label sourceName (e.g. "PERPLEXITY")
        // means the newsletter renderer would substitute the source label as the display
        // title, which is no better than showing nothing.
        if (isDomainTitle(t) && sourceName != null
                && LABEL_TITLES.contains(sourceName.trim().toUpperCase())) {
            return false;
        }

        return true;
    }

    /**
     * Returns true when a title is a bare domain/URL pattern rather than a real headline.
     * Matches hostnames like "pmc.ncbi.nlm.nih.gov" or "who.int — news".
     */
    private boolean isDomainTitle(String title) {
        String base = title.split("\\s[—\\-]\\s")[0].trim();
        return base.matches("[a-z0-9][a-z0-9.\\-]*\\.[a-z]{2,}(/[\\S]*)?");
    }
}

package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure domain service that transforms a list of {@link Contradiction} records
 * into a single {@link NewsletterSection} for the "Reversal Watch" newsletter
 * feature.
 *
 * <p>This builder formats contradiction data directly from structured records
 * without requiring an LLM call — the prior/new claims already carry the
 * editorial substance.  Each contradiction is rendered as a prior-vs-new
 * claim pair keyed to its wiki page slug.
 *
 * <p>Returns {@code null} when the contradiction list is empty, signaling
 * the caller to omit the section entirely.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-07-05
 * @updated 2026-08-24
 */
@Slf4j
public class ReversalWatchSectionBuilder {

    /**
     * Matches "Article [N]", "Article N", "Articles N, M and P" — compilation-session
     * position references produced by the wiki LLM that have no meaning in the newsletter.
     * Stripped from claim text so subscribers don't see meaningless "[1]" dead references.
     */
    private static final Pattern ARTICLE_REF = Pattern.compile(
            "\\bArticles?\\s+(?:\\[\\d+\\]|\\d+)(?:[,\\s]+(?:and\\s+)?(?:\\[\\d+\\]|\\d+))*\\s*",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Builds a REVERSAL_WATCH newsletter section from the given contradictions.
     *
     * @param contradictions recent contradictions detected by wiki compilation
     * @param sectionId      unique section identifier (e.g. "section-005")
     * @return a {@link NewsletterSection} summarizing the contradictions,
     *         or {@code null} if the list is null or empty
     */
    public NewsletterSection build(List<Contradiction> contradictions, String sectionId) {
        log.debug("build() | contradictions={}, sectionId={}",
                contradictions == null ? "null" : contradictions.size(), sectionId);

        if (contradictions == null || contradictions.isEmpty()) {
            log.debug("build() | return=null (no contradictions)");
            return null;
        }

        String headline = "Reversal Watch: %d Contradiction%s Detected".formatted(
                contradictions.size(),
                contradictions.size() == 1 ? "" : "s");

        // Format: PRIOR:<topic>|<prior claim>\nNEW:<new claim>
        // Renderer detects these prefixes to bold the prior claim and bullet the new claim.
        StringBuilder summary = new StringBuilder();
        for (Contradiction c : contradictions) {
            if (!summary.isEmpty()) {
                summary.append("\n");
            }
            String topic = c.pageSlug().replace("-", " ");
            String topicLabel = topic.substring(0, 1).toUpperCase() + topic.substring(1);
            summary.append("PRIOR:").append(topicLabel).append("|").append(c.priorClaim());
            summary.append("\n");
            summary.append("NEW:").append(stripArticleRefs(c.newClaim()));
        }

        // Collect article IDs from all source refs
        Set<String> articleIdSet = new LinkedHashSet<>();
        for (Contradiction c : contradictions) {
            for (SourceRef ref : c.priorSources()) {
                articleIdSet.add(ref.articleId());
            }
            for (SourceRef ref : c.newSources()) {
                articleIdSet.add(ref.articleId());
            }
        }

        // Fallback if sources carry no article IDs
        List<String> articleIds = new ArrayList<>(articleIdSet);
        if (articleIds.isEmpty()) {
            articleIds.add("contradiction-summary");
        }

        NewsletterSection result = new NewsletterSection(
                sectionId,
                SectionType.REVERSAL_WATCH,
                "Reversal Watch",
                headline,
                summary.toString(),
                articleIds
        );

        log.debug("build() | return={}", result);
        return result;
    }

    /**
     * Removes compilation-session article position references from a claim string.
     *
     * <p>The wiki compilation LLM receives articles numbered [1], [2], … and sometimes
     * writes claims like "Article [1] (Forbes) reports..." or "Articles 27 and 17 state...".
     * Those position numbers are meaningless outside the compilation session.  This method
     * strips them so the output reads naturally: "(Forbes) reports..." or "state...".
     */
    String stripArticleRefs(String claim) {
        log.debug("stripArticleRefs() | claim length={}", claim == null ? 0 : claim.length());
        if (claim == null || claim.isBlank()) {
            log.debug("stripArticleRefs() | return=empty");
            return claim == null ? "" : claim;
        }
        String result = ARTICLE_REF.matcher(claim).replaceAll("").trim();
        log.debug("stripArticleRefs() | return={}", result);
        return result;
    }
}

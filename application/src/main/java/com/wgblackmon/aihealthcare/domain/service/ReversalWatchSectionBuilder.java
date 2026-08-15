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
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
@Slf4j
public class ReversalWatchSectionBuilder {

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

        StringBuilder summary = new StringBuilder();
        for (Contradiction c : contradictions) {
            if (!summary.isEmpty()) {
                summary.append("\n");
            }
            String topic = c.pageSlug().replace("-", " ");
            summary.append(topic.substring(0, 1).toUpperCase()).append(topic.substring(1))
                    .append(": Was \"").append(c.priorClaim()).append("\"")
                    .append(" — Now: \"").append(c.newClaim()).append("\"");
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
}

package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record containing the result of a wiki gap analysis
 * run — the gaps identified and an overall coverage summary.
 *
 * @param gaps              list of identified knowledge gaps with article references
 * @param summary           overall assessment of wiki coverage
 * @param articlesAnalyzed  number of recent articles compared
 * @param wikiPagesChecked  number of existing wiki pages compared
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
public record WikiGapAnalysisResult(
        List<GapItem> gaps,
        String summary,
        int articlesAnalyzed,
        int wikiPagesChecked
) {

    /**
     * Compact canonical constructor — validates required fields and
     * creates defensive copies of all list fields.
     */
    public WikiGapAnalysisResult {
        if (gaps == null) {
            throw new IllegalArgumentException("gaps must not be null");
        }
        if (summary == null || summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        if (articlesAnalyzed < 0) {
            throw new IllegalArgumentException("articlesAnalyzed must be >= 0, got: " + articlesAnalyzed);
        }
        if (wikiPagesChecked < 0) {
            throw new IllegalArgumentException("wikiPagesChecked must be >= 0, got: " + wikiPagesChecked);
        }
        gaps = List.copyOf(gaps);
    }
}

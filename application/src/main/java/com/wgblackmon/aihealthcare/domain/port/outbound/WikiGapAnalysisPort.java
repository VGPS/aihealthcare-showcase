package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.WikiGapAnalysisResult;
import com.wgblackmon.aihealthcare.infrastructure.persistence.WikiPageEntity;

import java.util.List;

/**
 * Outbound port — analyze recent articles against existing wiki pages
 * to identify knowledge gaps (topics with article coverage but no wiki page).
 *
 * <p>Unlike {@link KnowledgeCompilationPort} which compiles articles INTO
 * wiki pages, this port COMPARES articles against pages and identifies
 * what is missing. Implementations use an LLM to classify topics.
 *
 * <p>Each identified gap includes the specific article IDs that cover the
 * topic, enabling the admin to review evidence before approving compilation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-11
 * @updated 2026-08-11
 */
public interface WikiGapAnalysisPort {

    /**
     * Analyze recent articles against existing wiki pages to identify
     * knowledge gaps.
     *
     * @param recentArticles articles to analyze; must not be null
     * @param existingPages  current wiki pages for coverage comparison
     * @return analysis result with gaps, article references, and summary
     */
    WikiGapAnalysisResult analyzeGaps(List<NewsArticle> recentArticles,
                                       List<WikiPageEntity> existingPages);
}

package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;

import java.util.List;

/**
 * Outbound port — score articles for technological significance using an LLM.
 *
 * <p>Implementations live in {@code infrastructure/ai} and send a batch of
 * articles to an LLM with a scoring rubric prompt. The LLM evaluates each
 * article and returns only those meeting the score threshold, along with
 * a one-sentence rationale for each score.
 *
 * <p>This port is separate from {@link AiSummarizationPort} because article
 * scoring is an evaluation task, not a content generation task.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-24
 * @updated 2026-07-24
 */
public interface ArticleScoringPort {

    /**
     * Score a batch of articles for technological significance within a theme.
     *
     * @param articles         the articles to score; must not be empty
     * @param theme            the trend keyword or theme name (e.g. "radiology ai")
     * @param themeDescription a brief description of the theme for LLM context
     * @param scoreThreshold   minimum score to include in results (e.g. 7)
     * @return scored articles meeting the threshold, sorted by score descending;
     *         may be empty if no articles meet the threshold
     */
    List<ScoredArticle> scoreArticles(List<NewsArticle> articles,
                                       String theme,
                                       String themeDescription,
                                       int scoreThreshold);

}

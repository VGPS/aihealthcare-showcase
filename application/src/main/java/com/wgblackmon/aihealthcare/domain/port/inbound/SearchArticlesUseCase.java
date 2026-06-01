package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Inbound port for multi-field article search.
 *
 * <p>Accepts an {@link ArticleSearchCriteria} with optional filter fields and
 * returns all articles matching the AND-combined predicates. When the criteria
 * is empty (all fields null), an empty list is returned to prevent accidental
 * full-table scans.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-01
 * @updated 2026-06-01
 */
public interface SearchArticlesUseCase {

    /**
     * Searches persisted articles using the given optional criteria.
     *
     * @param criteria filter fields — all optional, AND-combined when non-null
     * @return matching articles; empty list if no criteria specified or no matches
     */
    List<NewsArticle> search(ArticleSearchCriteria criteria);
}

package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for criteria-based article search against the persistence layer.
 *
 * <p>Distinct from {@link ArticleSearchPort} which performs vector-similarity
 * search. This port builds dynamic queries from the optional fields in
 * {@link ArticleSearchCriteria}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-01
 * @updated 2026-06-01
 */
public interface ArticleSearchQueryPort {

    /**
     * Finds articles matching the given criteria.
     *
     * <p>Only non-null fields in the criteria are applied as predicates,
     * AND-combined. Text fields use case-insensitive substring matching;
     * {@code sourceTier} uses exact match; date fields use range bounds.
     *
     * @param criteria the search criteria with optional filter fields
     * @return matching articles; empty list if no matches found
     */
    List<NewsArticle> findByCriteria(ArticleSearchCriteria criteria);
}

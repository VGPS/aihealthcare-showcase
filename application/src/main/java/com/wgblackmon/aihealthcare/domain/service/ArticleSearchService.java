package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.ArticleSearchCriteria;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.inbound.SearchArticlesUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleSearchQueryPort;

import java.util.Collections;
import java.util.List;

/**
 * Domain service implementing multi-field article search.
 *
 * <p>Delegates to {@link ArticleSearchQueryPort} for persistence-layer querying.
 * Returns an empty list when the criteria is empty (all fields null) to avoid
 * accidental full-table scans.
 *
 * <p>This class has no Spring annotations — it is wired as a bean in
 * {@link com.wgblackmon.aihealthcare.infrastructure.config.AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-06-01
 * @updated 2026-06-01
 */
public class ArticleSearchService implements SearchArticlesUseCase {

    private final ArticleSearchQueryPort queryPort;

    public ArticleSearchService(ArticleSearchQueryPort queryPort) {
        this.queryPort = queryPort;
    }

    @Override
    public List<NewsArticle> search(ArticleSearchCriteria criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return Collections.emptyList();
        }
        return queryPort.findByCriteria(criteria);
    }
}

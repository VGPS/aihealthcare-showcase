package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for semantic similarity search over persisted articles.
 *
 * <p>Implementations query a vector store using an embedding of the given
 * {@code query} string and return the {@code topK} most semantically similar
 * {@link NewsArticle} records.  The port is intentionally simple — callers
 * decide how to consume or rank the results.
 *
 * <p>This port belongs in the domain layer because it expresses a pure
 * business capability (find relevant articles) without leaking any
 * infrastructure concern (Spring AI, vector store technology, etc.).
 * The concrete adapter lives in {@code infrastructure.ai}.
 *
 * <p>An empty list is always a valid return value (e.g., when the vector
 * store has not yet been populated).  Callers must handle the empty case
 * gracefully.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
public interface ArticleSearchPort {

    /**
     * Returns up to {@code topK} articles most semantically similar to {@code query}.
     *
     * @param query the natural-language query or topic name to search against
     * @param topK  the maximum number of results to return
     * @return list of matching articles ordered by descending similarity score;
     *         never {@code null}, may be empty
     */
    List<NewsArticle> findSimilar(String query, int topK);
}

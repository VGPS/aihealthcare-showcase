package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port — compile newly harvested articles into the
 * LLM-maintained knowledge wiki.
 *
 * <p><strong>Ownership contract:</strong> implementations of this port
 * read the supplied {@link NewsArticle} records but never mutate them.
 * The wiki layer (pages, contradictions, revision history) is entirely
 * owned by the compilation adapter.  Raw sources remain immutable.
 *
 * <p>A single compilation run processes a batch of new articles,
 * creating or updating wiki pages and detecting contradictions
 * between prior claims and newly harvested information.  The result
 * is a {@link CompilationReport} capturing everything that changed.
 *
 * <p>Adapter implementations will live in {@code infrastructure.ai}
 * (Slice W2).  Unit tests inject mock implementations so no real
 * AI calls are made outside the {@code ai-integration} Spring profile.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public interface KnowledgeCompilationPort {

    /**
     * Compile the given newly harvested articles into the wiki,
     * creating or updating pages and flagging contradictions.
     *
     * @param newArticles articles to integrate; must not be {@code null}
     *                    or empty.  These are read-only — the port never
     *                    mutates the supplied records.
     * @return a report summarizing pages created, updated, and
     *         contradictions flagged during this compilation run
     */
    CompilationReport compileNewSources(List<NewsArticle> newArticles);
}

package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for exporting research results to an external corpus store
 * (e.g. the NotebookLM directory on disk).
 *
 * <p>Called by {@link com.wgblackmon.aihealthcare.domain.service.ResearchOrchestratorService}
 * after a STAGED_RESEARCH run completes, so that every set of Perplexity-sourced
 * articles is appended to the on-disk corpus that NotebookLM indexes.
 *
 * <p>Implementations must swallow I/O errors and log them rather than propagating
 * them — a failed export must never abort the research response returned to the caller.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
public interface ResearchExportPort {

    /**
     * Exports the given articles under the specified label.
     *
     * @param label    human-readable title for the export (e.g. "Research: AI diagnostics")
     * @param articles articles to export; may be empty (no-op in that case)
     */
    void export(String label, List<NewsArticle> articles);
}

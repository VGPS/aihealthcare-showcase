package com.wgblackmon.aihealthcare.infrastructure.delivery;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ResearchExportPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * {@link ResearchExportPort} adapter that delegates to {@link NotebookLMService}.
 *
 * <p>Called by {@link com.wgblackmon.aihealthcare.domain.service.ResearchOrchestratorService}
 * after each successful STAGED_RESEARCH run to export Perplexity-sourced articles to the
 * NotebookLM on-disk corpus.
 *
 * <p>As mandated by the {@link ResearchExportPort} contract, {@link IOException}s are
 * caught and logged rather than propagated — a failed export must never abort the
 * research response returned to the caller.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-06
 * @updated 2026-05-06
 */
@Slf4j
@Component
public class NotebookLMResearchExportAdapter implements ResearchExportPort {

    private final NotebookLMService notebookLMService;

    /**
     * Constructs the adapter with the NotebookLM filesystem export service.
     *
     * @param notebookLMService Service that writes articles to the NotebookLM directory.
     */
    public NotebookLMResearchExportAdapter(NotebookLMService notebookLMService) {
        log.debug("NotebookLMResearchExportAdapter() | notebookLMService={}",
                  notebookLMService.getClass().getSimpleName());
        this.notebookLMService = notebookLMService;
        log.debug("NotebookLMResearchExportAdapter() | return=void");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link NotebookLMService#export}.  Any {@link IOException}
     * is caught, logged as a warning, and swallowed so that the research response
     * is never blocked by a filesystem failure.
     */
    @Override
    public void export(String label, List<NewsArticle> articles) {
        log.debug("export() | label={}, articleCount={}", label, articles == null ? 0 : articles.size());

        if (articles == null || articles.isEmpty()) {
            log.debug("export() | no articles — skipping export");
            log.debug("export() | return=void");
            return;
        }

        try {
            notebookLMService.export(label, articles);
            log.info("export() | NotebookLM export complete: label='{}', articles={}",
                     label, articles.size());
        } catch (IOException e) {
            log.warn("export() | NotebookLM export failed — swallowing to avoid blocking research response: {}",
                     e.getMessage());
        }

        log.debug("export() | return=void");
    }
}

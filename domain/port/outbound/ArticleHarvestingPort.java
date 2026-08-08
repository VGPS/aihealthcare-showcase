package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for harvesting raw articles from external feed sources.
 *
 * <p>Implementations (adapters) are responsible for connecting to RSS/Atom
 * feeds, REST APIs (PubMed E-utilities, FDA API, ClinicalTrials.gov, etc.),
 * or any other external data source and returning a normalized list of
 * {@link NewsArticle} domain records.
 *
 * <p>Callers (use-case interactors and scheduled harvesters) depend only on
 * this interface — never on concrete adapter classes — keeping the domain and
 * application layers free of infrastructure concerns.
 *
 * <p>This port is distinct from {@link ArticleIngestionPort}: harvesting pulls
 * articles from external feeds in bulk; ingestion retrieves stored articles for
 * newsletter generation.  Slice 2 will wire a persistence layer between them.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-10
 * @updated 2026-04-10
 */
public interface ArticleHarvestingPort {

    /**
     * Harvests articles from all configured sources in a single cycle.
     *
     * @return flat list of harvested {@link NewsArticle} records;
     *         never {@code null}, may be empty if all sources fail or are unreachable
     */
    List<NewsArticle> harvestAll();
}

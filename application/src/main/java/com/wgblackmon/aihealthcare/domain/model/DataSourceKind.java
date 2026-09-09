package com.wgblackmon.aihealthcare.domain.model;

/**
 * Classifies the origin of an enterprise data feed.
 *
 * <p>{@code INTERNAL_CORPUS} queries the application's own Postgres/pgvector
 * tables. {@code LLM_SYNTHESIS} produces a cited prose answer over that corpus.
 * {@code CUSTOMER_REMOTE} fetches data from an HTTPS endpoint on the
 * customer's side.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public enum DataSourceKind {

    INTERNAL_CORPUS,
    LLM_SYNTHESIS,
    CUSTOMER_REMOTE
}

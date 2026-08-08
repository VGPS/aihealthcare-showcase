package com.wgblackmon.aihealthcare.domain.model;

/**
 * Enumeration of wiki page categories used by the LLM-maintained
 * knowledge base.
 *
 * <p>Each compiled wiki page is classified into one of these types
 * so the query layer can filter and rank results by structural role:
 * <ul>
 *   <li>{@link #ENTITY} — a single actor (company, product, regulation)</li>
 *   <li>{@link #CONCEPT} — a technique, trend, or domain idea</li>
 *   <li>{@link #COMPARISON} — side-by-side analysis of two or more entities</li>
 *   <li>{@link #OVERVIEW} — broad landscape or market summary</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public enum WikiPageType {
    ENTITY,
    CONCEPT,
    COMPARISON,
    OVERVIEW
}

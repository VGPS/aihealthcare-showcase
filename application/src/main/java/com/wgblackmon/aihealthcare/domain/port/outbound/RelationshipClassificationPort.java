package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.CompanyRelationship;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;

import java.util.List;

/**
 * Outbound port for LLM-powered company relationship classification.
 *
 * <p>Takes keyword-pre-filtered candidate articles and uses an LLM to
 * confirm or reject inter-company relationships, extracting clean
 * source/target company names rather than a fixed word-window around
 * a trigger phrase. Articles that are not real relationships are
 * filtered out.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-07
 * @updated 2026-10-07
 */
public interface RelationshipClassificationPort {

    /**
     * Classifies a batch of candidate articles into company relationships using LLM.
     * Articles that do not describe a real relationship are filtered out.
     *
     * @param candidateArticles articles that passed keyword pre-filtering
     * @return classified company relationships with clean source/target names
     */
    List<CompanyRelationship> classifyRelationships(List<NewsArticle> candidateArticles);
}

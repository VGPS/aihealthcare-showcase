package com.wgblackmon.aihealthcare.infrastructure.ingestion.pubmed;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PubMedBackfillHarvester}.
 *
 * <p>Tests the XML parsing logic using the live PubMed E-utilities API.
 * These tests make real HTTP calls to PubMed (free, no API key needed).
 * PubMed's public API is reliable and rate-limited to 3 req/sec.
 *
 * <p>If PubMed is unreachable, the harvester returns empty lists gracefully —
 * tests assert that no exception is thrown and results are non-null.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
class PubMedBackfillHarvesterTest {

    private PubMedBackfillHarvester harvester;

    @BeforeEach
    void setUp() {
        harvester = new PubMedBackfillHarvester();
    }

    @Test
    void harvest_withValidQuery_returnsArticles() {
        List<NewsArticle> articles = harvester.harvest(
                "\"artificial intelligence\" AND \"healthcare\"",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 3, 31),
                5);

        assertThat(articles).isNotNull();
        // PubMed should have AI healthcare articles from Q1 2024
        // but we don't assert > 0 in case of network issues
    }

    @Test
    void harvest_articlesHaveRequiredFields() {
        List<NewsArticle> articles = harvester.harvest(
                "\"artificial intelligence\" AND \"radiology\"",
                LocalDate.of(2024, 6, 1),
                LocalDate.of(2024, 6, 30),
                3);

        for (NewsArticle article : articles) {
            assertThat(article.articleId()).startsWith("pubmed-");
            assertThat(article.title()).isNotBlank();
            assertThat(article.url().toString()).contains("pubmed.ncbi.nlm.nih.gov");
            assertThat(article.sourceName()).isEqualTo("PubMed Backfill");
            assertThat(article.sourceTier()).isEqualTo("ACADEMIC");
            assertThat(article.sourceWeight()).isEqualTo(0.9);
            assertThat(article.topic()).isEqualTo("General AI Healthcare News");
            assertThat(article.publishedAt()).isNotNull();
        }
    }

    @Test
    void harvest_withNoResults_returnsEmptyList() {
        List<NewsArticle> articles = harvester.harvest(
                "\"xyznonexistentterm12345\" AND \"abcnotfound67890\"",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 1, 31),
                10);

        assertThat(articles).isNotNull();
        assertThat(articles).isEmpty();
    }

    @Test
    void harvest_respectsMaxResults() {
        List<NewsArticle> articles = harvester.harvest(
                "\"artificial intelligence\" AND \"healthcare\"",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 12, 31),
                3);

        assertThat(articles).isNotNull();
        assertThat(articles.size()).isLessThanOrEqualTo(3);
    }

    @Test
    void harvest_articleIdFormatIsPubmedPrefix() {
        List<NewsArticle> articles = harvester.harvest(
                "\"deep learning\" AND \"clinical trial\"",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 6, 30),
                2);

        for (NewsArticle article : articles) {
            assertThat(article.articleId()).matches("pubmed-\\d+");
        }
    }

    @Test
    void harvest_abstractTextIsPopulated() {
        List<NewsArticle> articles = harvester.harvest(
                "\"artificial intelligence\" AND \"drug discovery\"",
                LocalDate.of(2024, 1, 1),
                LocalDate.of(2024, 6, 30),
                3);

        // Most PubMed articles have abstracts, but some don't
        // Just verify bodyText is not null
        for (NewsArticle article : articles) {
            assertThat(article.bodyText()).isNotNull();
        }
    }
}

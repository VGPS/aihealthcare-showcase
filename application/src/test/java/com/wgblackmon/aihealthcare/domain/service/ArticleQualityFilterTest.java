package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ArticleQualityFilter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-23
 * @updated 2026-08-23
 */
class ArticleQualityFilterTest {

    private ArticleQualityFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ArticleQualityFilter();
    }

    private NewsArticle article(String title, String sourceName, String bodyText) {
        return new NewsArticle(
                "art-" + System.nanoTime(),
                title,
                URI.create("https://example.com/article"),
                bodyText,
                "AI Healthcare",
                null, null, sourceName, null, 0.5, Instant.now()
        );
    }

    // --- usable articles ---

    @Test
    void realHeadline_withBody_isUsable() {
        assertThat(filter.isUsable(article(
                "FDA Clears AI Diagnostic Tool for Radiology",
                "Healthcare Dive",
                "The FDA cleared a new AI-powered radiology tool..."))).isTrue();
    }

    @Test
    void realHeadline_withoutBody_butLongTitle_isUsable() {
        assertThat(filter.isUsable(article(
                "New CMS Final Rule Expands Coverage for Remote Monitoring Devices",
                "CMS", null))).isTrue();
    }

    @Test
    void sourceName_contained_in_title_butTitleIsReal_isUsable() {
        // title is longer than sourceName — not an exact match
        assertThat(filter.isUsable(article(
                "Perplexity Raises $500M for Healthcare AI Expansion",
                "Perplexity", "Body text here"))).isTrue();
    }

    // --- unusable: known source-label titles ---

    @Test
    void perplexityLabel_isNotUsable() {
        assertThat(filter.isUsable(article("PERPLEXITY", "Perplexity Healthcare", null))).isFalse();
    }

    @Test
    void researchLabel_isNotUsable() {
        assertThat(filter.isUsable(article("RESEARCH", "Research Feed", "some body"))).isFalse();
    }

    @Test
    void academicLabel_isNotUsable() {
        assertThat(filter.isUsable(article("ACADEMIC", null, null))).isFalse();
    }

    @Test
    void industryLabel_isNotUsable() {
        assertThat(filter.isUsable(article("INDUSTRY", null, null))).isFalse();
    }

    @Test
    void huggingfaceLabel_isNotUsable() {
        assertThat(filter.isUsable(article("HUGGINGFACE", "HuggingFace", ""))).isFalse();
    }

    // --- unusable: title equals sourceName ---

    @Test
    void titleEqualsSourceName_caseInsensitive_isNotUsable() {
        assertThat(filter.isUsable(article("Healthcare Dive", "Healthcare Dive", "body text"))).isFalse();
    }

    @Test
    void titleEqualsSourceName_mixedCase_isNotUsable() {
        assertThat(filter.isUsable(article("HEALTHCARE DIVE", "Healthcare Dive", null))).isFalse();
    }

    // --- unusable: short title with no body ---

    @Test
    void shortTitleNoBody_isNotUsable() {
        assertThat(filter.isUsable(article("FDA", null, null))).isFalse();
    }

    @Test
    void shortTitleNoBody_blankBody_isNotUsable() {
        assertThat(filter.isUsable(article("CMS", "CMS.gov", "   "))).isFalse();
    }

    @Test
    void shortTitle_withBody_isUsable() {
        // 9 chars but has body — body text makes it useful
        assertThat(filter.isUsable(article("AI in FDA", "FDA", "Significant body content about FDA AI plans"))).isTrue();
    }
}

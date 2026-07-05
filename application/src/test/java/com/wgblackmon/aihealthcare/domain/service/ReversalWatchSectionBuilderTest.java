package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.Contradiction;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import com.wgblackmon.aihealthcare.domain.model.SourceRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReversalWatchSectionBuilder}.
 *
 * <p>Pure JUnit 5 — no mocks needed since the builder is a stateless
 * pure-Java service.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
class ReversalWatchSectionBuilderTest {

    private ReversalWatchSectionBuilder builder;

    private static final SourceRef PRIOR_SOURCE = new SourceRef(
            "article-prior-001", "FDA", LocalDate.of(2026, 6, 1), "Original guidance stated...");
    private static final SourceRef NEW_SOURCE = new SourceRef(
            "article-new-001", "FDA", LocalDate.of(2026, 7, 1), "Updated guidance now states...");

    private static final Contradiction CONTRADICTION = new Contradiction(
            "fda-ai-guidance",
            "AI diagnostic tools require full premarket review",
            "AI diagnostic tools may use predetermined change control plans",
            List.of(PRIOR_SOURCE),
            List.of(NEW_SOURCE),
            Instant.now()
    );

    @BeforeEach
    void setUp() {
        builder = new ReversalWatchSectionBuilder();
    }

    @Test
    @DisplayName("build() with null list returns null")
    void build_withNullList_returnsNull() {
        NewsletterSection result = builder.build(null, "section-001");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("build() with empty list returns null")
    void build_withEmptyList_returnsNull() {
        NewsletterSection result = builder.build(Collections.emptyList(), "section-001");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("build() with single contradiction returns REVERSAL_WATCH section")
    void build_withSingleContradiction_returnsSectionWithCorrectType() {
        NewsletterSection result = builder.build(List.of(CONTRADICTION), "section-005");

        assertThat(result).isNotNull();
        assertThat(result.sectionType()).isEqualTo(SectionType.REVERSAL_WATCH);
        assertThat(result.sectionId()).isEqualTo("section-005");
        assertThat(result.topic()).isEqualTo("Reversal Watch");
    }

    @Test
    @DisplayName("build() headline contains contradiction count (singular)")
    void build_withSingleContradiction_headlineContainsCount() {
        NewsletterSection result = builder.build(List.of(CONTRADICTION), "section-001");

        assertThat(result.headline()).contains("1 Contradiction Detected");
    }

    @Test
    @DisplayName("build() with multiple contradictions includes all claims in summary")
    void build_withMultipleContradictions_summaryContainsAllClaims() {
        Contradiction second = new Contradiction(
                "ai-billing-regulation",
                "AI billing codes approved for Medicare",
                "AI billing codes suspended pending review",
                List.of(PRIOR_SOURCE),
                List.of(NEW_SOURCE),
                Instant.now()
        );

        NewsletterSection result = builder.build(List.of(CONTRADICTION, second), "section-001");

        assertThat(result.headline()).contains("2 Contradictions Detected");
        assertThat(result.summary()).contains("fda-ai-guidance");
        assertThat(result.summary()).contains("ai-billing-regulation");
        assertThat(result.summary()).contains("AI diagnostic tools require full premarket review");
        assertThat(result.summary()).contains("AI billing codes suspended pending review");
    }

    @Test
    @DisplayName("build() extracts article IDs from prior and new sources")
    void build_extractsArticleIdsFromSources() {
        NewsletterSection result = builder.build(List.of(CONTRADICTION), "section-001");

        assertThat(result.articleIds()).contains("article-prior-001", "article-new-001");
    }

    @Test
    @DisplayName("build() with empty sources uses synthetic fallback ID")
    void build_withEmptySources_usesSyntheticId() {
        Contradiction noSources = new Contradiction(
                "empty-sources-page",
                "Prior claim text",
                "New claim text",
                Collections.emptyList(),
                Collections.emptyList(),
                Instant.now()
        );

        NewsletterSection result = builder.build(List.of(noSources), "section-001");

        assertThat(result.articleIds()).containsExactly("contradiction-summary");
    }
}

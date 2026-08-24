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
 * @version 1.1
 * @since   2026-07-05
 * @updated 2026-08-24
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
        assertThat(result.summary()).contains("Fda ai guidance");
        assertThat(result.summary()).contains("Ai billing regulation");
        assertThat(result.summary()).contains("AI diagnostic tools require full premarket review");
        assertThat(result.summary()).contains("AI billing codes suspended pending review");
        assertThat(result.summary()).contains("\n");
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

    // -------------------------------------------------------------------------
    // stripArticleRefs()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("stripArticleRefs() removes 'Article [N] (Source)' pattern")
    void stripArticleRefs_removesArticleBracketN() {
        String result = builder.stripArticleRefs("Article [1] (Forbes) reports that AI will replace doctors.");
        assertThat(result).isEqualTo("(Forbes) reports that AI will replace doctors.");
    }

    @Test
    @DisplayName("stripArticleRefs() removes 'Article N' plain number pattern")
    void stripArticleRefs_removesArticlePlainN() {
        String result = builder.stripArticleRefs("Article 5 (BBC Science Focus) reports AI is deciding who gets healthcare.");
        assertThat(result).isEqualTo("(BBC Science Focus) reports AI is deciding who gets healthcare.");
    }

    @Test
    @DisplayName("stripArticleRefs() removes 'Articles N and M' multi-article pattern")
    void stripArticleRefs_removesArticlesNandM() {
        String result = builder.stripArticleRefs("Articles 27 and 17 state the FDA has authorized approximately 1,500 devices.");
        assertThat(result).isEqualTo("state the FDA has authorized approximately 1,500 devices.");
    }

    @Test
    @DisplayName("stripArticleRefs() removes 'Articles N, M, and P' list pattern")
    void stripArticleRefs_removesArticlesList() {
        String result = builder.stripArticleRefs("Multiple sources (articles 29, 28, and 16) consistently report the FDA has authorized 1,500 devices.");
        // "articles 29, 28, and 16" is matched and removed
        assertThat(result).doesNotContain("articles 29");
        assertThat(result).contains("consistently report");
    }

    @Test
    @DisplayName("stripArticleRefs() leaves pubmed ID references unchanged")
    void stripArticleRefs_leavesPubmedIdsUnchanged() {
        String result = builder.stripArticleRefs("pubmed:42594845 finds that LLMs systematically overcode.");
        assertThat(result).isEqualTo("pubmed:42594845 finds that LLMs systematically overcode.");
    }

    @Test
    @DisplayName("stripArticleRefs() leaves clean publication-name claims unchanged")
    void stripArticleRefs_leavesCleanClaimsUnchanged() {
        String result = builder.stripArticleRefs("Forbes coverage frames the autonomous AI replacement argument as mainstream.");
        assertThat(result).isEqualTo("Forbes coverage frames the autonomous AI replacement argument as mainstream.");
    }

    @Test
    @DisplayName("build() summary does not contain 'Article [N]' references")
    void build_summaryDoesNotContainArticleRefNumbers() {
        Contradiction withBadRef = new Contradiction(
                "ai-doctor-debate",
                "Prior claim about AI doctors.",
                "Article [1] (Forbes) reports that autonomous AI should replace doctors entirely.",
                List.of(PRIOR_SOURCE),
                List.of(NEW_SOURCE),
                Instant.now()
        );

        NewsletterSection result = builder.build(List.of(withBadRef), "section-001");

        assertThat(result.summary()).doesNotContain("Article [1]");
        assertThat(result.summary()).contains("(Forbes) reports that autonomous AI");
    }
}

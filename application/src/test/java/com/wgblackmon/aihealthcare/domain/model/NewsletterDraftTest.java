package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link NewsletterDraft} domain record.
 *
 * <p>Verifies field validation in the compact constructor and that defensive copies
 * of {@code sections} and {@code sourceArticles} prevent external mutation.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-04
 * @updated 2026-04-04
 */
class NewsletterDraftTest {

    // --- shared fixtures ---

    private static final NewsArticle ARTICLE = new NewsArticle(
            "article-001",
            "AI Improves Diagnostic Accuracy",
            URI.create("https://example.com/article-001"),
            "Researchers found that AI models outperform radiologists.",
            "AI diagnostics",
            null,  // author — optional
            null   // publishedDate — optional
    );

    private static final NewsletterSection SECTION = new NewsletterSection(
            "section-001",
            SectionType.WHAT_SHIPPED,
            "AI diagnostics",
            "AI Outperforms Radiologists",
            "A new study confirms AI-assisted diagnosis improves accuracy by 20%.",
            List.of("article-001")
    );

    private NewsletterDraft validDraft() {
        return new NewsletterDraft(
                "draft-001",
                "run-001",
                "AI in Healthcare Weekly",
                LocalDate.of(2025, 1, 27),
                "Welcome to this week's edition.",
                List.of(SECTION),
                List.of(ARTICLE),
                Instant.now()
        );
    }

    @Test
    void validDraft_constructsSuccessfully() {
        NewsletterDraft draft = validDraft();

        assertThat(draft.draftId()).isEqualTo("draft-001");
        assertThat(draft.runId()).isEqualTo("run-001");
        assertThat(draft.title()).isEqualTo("AI in Healthcare Weekly");
        assertThat(draft.sections()).hasSize(1);
        assertThat(draft.sourceArticles()).hasSize(1);
        assertThat(draft.generatedAt()).isNotNull();
    }

    @Test
    void sections_areDefensivelyCopied() {
        List<NewsletterSection> mutable = new ArrayList<>(List.of(SECTION));
        NewsletterDraft draft = new NewsletterDraft(
                "draft-001", "run-001", "Title", LocalDate.now(),
                "Intro.", mutable, List.of(ARTICLE), Instant.now()
        );

        mutable.add(SECTION);

        assertThat(draft.sections()).hasSize(1);
    }

    @Test
    void sourceArticles_areDefensivelyCopied() {
        List<NewsArticle> mutable = new ArrayList<>(List.of(ARTICLE));
        NewsletterDraft draft = new NewsletterDraft(
                "draft-001", "run-001", "Title", LocalDate.now(),
                "Intro.", List.of(SECTION), mutable, Instant.now()
        );

        mutable.add(ARTICLE);

        assertThat(draft.sourceArticles()).hasSize(1);
    }

    @Test
    void blankDraftId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterDraft(
                "", "run-001", "Title", LocalDate.now(),
                "Intro.", List.of(SECTION), List.of(ARTICLE), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("draftId");
    }

    @Test
    void blankRunId_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterDraft(
                "draft-001", "  ", "Title", LocalDate.now(),
                "Intro.", List.of(SECTION), List.of(ARTICLE), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId");
    }

    @Test
    void nullWeekOf_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterDraft(
                "draft-001", "run-001", "Title", null,
                "Intro.", List.of(SECTION), List.of(ARTICLE), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weekOf");
    }

    @Test
    void emptySections_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterDraft(
                "draft-001", "run-001", "Title", LocalDate.now(),
                "Intro.", List.of(), List.of(ARTICLE), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sections");
    }

    @Test
    void emptySourceArticles_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterDraft(
                "draft-001", "run-001", "Title", LocalDate.now(),
                "Intro.", List.of(SECTION), List.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceArticles");
    }

    @Test
    void nullGeneratedAt_throwsIllegalArgument() {
        assertThatThrownBy(() -> new NewsletterDraft(
                "draft-001", "run-001", "Title", LocalDate.now(),
                "Intro.", List.of(SECTION), List.of(ARTICLE), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generatedAt");
    }
}

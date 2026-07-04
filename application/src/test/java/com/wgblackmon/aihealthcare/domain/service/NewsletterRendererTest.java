package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterDraft;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.SectionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NewsletterRenderer}.
 *
 * <p>Pure JUnit 5 — no Spring context, no mocks.  The renderer is a stateless
 * plain Java class and can be constructed directly.  Tests verify that both
 * rendering modes (HTML and plain text) produce non-blank output containing the
 * expected structural markers and content fields from the draft fixture.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-07-03
 */
class NewsletterRendererTest {

    private NewsletterRenderer renderer;
    private NewsletterDraft    draft;

    @BeforeEach
    void setUp() {
        renderer = new NewsletterRenderer();

        NewsArticle article = new NewsArticle(
                "article-001",
                "AI Improves Diagnostic Accuracy",
                URI.create("https://example.com/article-001"),
                "Researchers found that AI models outperform radiologists.",
                "AI diagnostics",
                "Dr. Jane Smith",
                1L, "PubMed AI Healthcare", "ACADEMIC", 0.9,
                Instant.parse("2026-04-01T00:00:00Z")
        );

        NewsletterSection section = new NewsletterSection(
                "section-001",
                SectionType.WHAT_SHIPPED,
                "AI diagnostics",
                "AI Outperforms Radiologists",
                "A new study confirms AI-assisted diagnosis improves accuracy by 20%.",
                List.of("article-001")
        );

        draft = new NewsletterDraft(
                "draft-001",
                "run-001",
                "AI in Healthcare Weekly",
                LocalDate.of(2026, 4, 11),
                "Welcome to this week's AI in Healthcare newsletter.",
                List.of(section),
                List.of(article),
                Instant.parse("2026-04-11T06:00:00Z")
        );
    }

    // -------------------------------------------------------------------------
    // renderHtml()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("renderHtml() returns non-blank HTML string")
    void renderHtml_returnsNonBlankString() {
        String html = renderer.renderHtml(draft);
        assertThat(html).isNotBlank();
    }

    @Test
    @DisplayName("renderHtml() output contains required HTML structure")
    void renderHtml_containsHtmlStructure() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("<html", "</html>", "<body", "</body>");
    }

    @Test
    @DisplayName("renderHtml() contains newsletter title")
    void renderHtml_containsTitle() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("AI in Healthcare Weekly");
    }

    @Test
    @DisplayName("renderHtml() contains section headline")
    void renderHtml_containsSectionHeadline() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("AI Outperforms Radiologists");
    }

    @Test
    @DisplayName("renderHtml() contains section summary")
    void renderHtml_containsSectionSummary() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("AI-assisted diagnosis improves accuracy by 20%");
    }

    @Test
    @DisplayName("renderHtml() contains source article link")
    void renderHtml_containsSourceArticleLink() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("https://example.com/article-001");
        assertThat(html).contains("<a href=");
    }

    @Test
    @DisplayName("renderHtml() contains introduction")
    void renderHtml_containsIntroduction() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("Welcome to this week");
    }

    // -------------------------------------------------------------------------
    // renderPlainText()
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("renderPlainText() returns non-blank string")
    void renderPlainText_returnsNonBlankString() {
        String text = renderer.renderPlainText(draft);
        assertThat(text).isNotBlank();
    }

    @Test
    @DisplayName("renderPlainText() contains no HTML tags")
    void renderPlainText_containsNoHtmlTags() {
        String text = renderer.renderPlainText(draft);
        assertThat(text).doesNotContain("<html", "<body", "<h1", "<p", "<div");
    }

    @Test
    @DisplayName("renderPlainText() contains newsletter title")
    void renderPlainText_containsTitle() {
        String text = renderer.renderPlainText(draft);
        assertThat(text).contains("AI in Healthcare Weekly");
    }

    @Test
    @DisplayName("renderPlainText() contains section separator")
    void renderPlainText_containsSectionSeparator() {
        String text = renderer.renderPlainText(draft);
        assertThat(text).contains("---");
    }

    @Test
    @DisplayName("renderPlainText() contains source article URL")
    void renderPlainText_containsSourceUrl() {
        String text = renderer.renderPlainText(draft);
        assertThat(text).contains("https://example.com/article-001");
    }

    @Test
    @DisplayName("renderPlainText() contains SOURCES heading")
    void renderPlainText_containsSourcesHeading() {
        String text = renderer.renderPlainText(draft);
        assertThat(text).contains("SOURCES");
    }

    // -------------------------------------------------------------------------
    // Email template structure (Slice 44)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("renderHtml() uses table-based layout for email compatibility")
    void renderHtml_usesTableLayout() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("role=\"presentation\"");
        assertThat(html).contains("max-width: 600px");
    }

    @Test
    @DisplayName("renderHtml() contains branded header banner")
    void renderHtml_containsHeaderBanner() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("background-color: #1a1a2e");
    }

    @Test
    @DisplayName("renderHtml() contains unsubscribe link in footer")
    void renderHtml_containsUnsubscribeFooter() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("{{unsubscribe_url}}");
        assertThat(html).contains("Unsubscribe");
    }

    @Test
    @DisplayName("renderHtml() contains manage preferences link in footer")
    void renderHtml_containsPreferencesFooter() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("{{preferences_url}}");
        assertThat(html).contains("Manage preferences");
    }

    @Test
    @DisplayName("renderHtml() contains platform branding in footer")
    void renderHtml_containsPlatformBranding() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("AIHealthcare");
        assertThat(html).contains("AI-in-Healthcare Intelligence Platform");
    }

    @Test
    @DisplayName("renderPlainText() contains unsubscribe footer")
    void renderPlainText_containsUnsubscribeFooter() {
        String text = renderer.renderPlainText(draft);
        assertThat(text).contains("Unsubscribe: {{unsubscribe_url}}");
        assertThat(text).contains("Manage preferences: {{preferences_url}}");
    }
}

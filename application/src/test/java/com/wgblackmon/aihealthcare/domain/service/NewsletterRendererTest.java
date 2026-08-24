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
    @DisplayName("renderPlainText() contains SOURCE ARTICLES heading")
    void renderPlainText_containsSourceArticlesHeading() {
        String text = renderer.renderPlainText(draft);
        assertThat(text).contains("SOURCE ARTICLES");
    }

    @Test
    @DisplayName("renderPlainText() includes publication name next to source article title")
    void renderPlainText_includesSourcePublicationName() {
        String text = renderer.renderPlainText(draft);
        assertThat(text).contains("AI Improves Diagnostic Accuracy (PubMed AI Healthcare)");
    }

    @Test
    @DisplayName("renderHtml() shows 'Used in:' attribution under each source article")
    void renderHtml_sourceArticleShowsUsedInSection() {
        String html = renderer.renderHtml(draft);
        // The source article "article-001" is used by "section-001" whose headline is "AI Outperforms Radiologists"
        assertThat(html).contains("Used in:");
        assertThat(html).contains("AI Outperforms Radiologists");
    }

    @Test
    @DisplayName("renderPlainText() shows 'Used in:' attribution under each source article")
    void renderPlainText_sourceArticleShowsUsedInSection() {
        String text = renderer.renderPlainText(draft);
        assertThat(text).contains("Used in: AI Outperforms Radiologists");
    }

    @Test
    @DisplayName("renderHtml() makes [1] citation in summary a clickable link to the source article")
    void renderHtml_inlineCitationLinksToSourceArticle() {
        // Build a section whose summary contains [1] — should become a hyperlink
        NewsArticle citedArticle = new NewsArticle(
                "art-cited",
                "AI Outperforms Radiologists in Study",
                URI.create("https://example.com/cited"),
                "body text",
                "AI diagnostics",
                null, null, "PubMed", "ACADEMIC", 0.9, null
        );
        NewsletterSection citingSection = new NewsletterSection(
                "section-cite",
                SectionType.WHAT_SHIPPED,
                "AI diagnostics",
                "Radiology AI Milestone",
                "A landmark study [1] found AI outperforms radiologists.",
                List.of("art-cited")
        );
        NewsletterDraft citingDraft = new NewsletterDraft(
                "draft-cite", "run-cite", "AI Weekly",
                LocalDate.of(2026, 8, 24),
                "Intro.",
                List.of(citingSection),
                List.of(citedArticle),
                java.time.Instant.now()
        );

        String html = renderer.renderHtml(citingDraft);
        // [1] should be replaced with a hyperlink to the cited article URL
        assertThat(html).contains("href=\"https://example.com/cited\"");
        // The link text should contain [1]
        assertThat(html).contains("[1]");
    }

    @Test
    @DisplayName("renderHtml() shows Source Articles heading (not Sources)")
    void renderHtml_containsSourceArticlesHeading() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("Source Articles");
    }

    @Test
    @DisplayName("renderHtml() includes publication name in source articles list")
    void renderHtml_sourceArticlesListIncludesPublicationName() {
        String html = renderer.renderHtml(draft);
        assertThat(html).contains("PubMed AI Healthcare");
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

    // -------------------------------------------------------------------------
    // Reversal Watch section rendering
    // -------------------------------------------------------------------------

    private NewsletterDraft draftWithReversalWatch() {
        NewsArticle article = new NewsArticle(
                "article-001", "Test Article",
                URI.create("https://example.com/article-001"),
                "Body text.", "AI diagnostics", null,
                1L, "PubMed", "ACADEMIC", 0.9, null
        );
        NewsletterSection reversalSection = new NewsletterSection(
                "section-rw",
                SectionType.REVERSAL_WATCH,
                "Reversal Watch",
                "Reversal Watch: 1 Contradiction Detected",
                "FDA reversed its prior guidance on AI diagnostics.",
                List.of("article-001")
        );
        return new NewsletterDraft(
                "draft-rw", "run-rw", "AI Weekly",
                LocalDate.of(2026, 7, 5),
                "Welcome to the newsletter.",
                List.of(reversalSection),
                List.of(article),
                Instant.now()
        );
    }

    @Test
    @DisplayName("renderHtml() uses warning color for REVERSAL_WATCH section")
    void renderHtml_reversalWatchSection_usesWarningColor() {
        String html = renderer.renderHtml(draftWithReversalWatch());
        assertThat(html).contains("#cc3300");
        assertThat(html).contains("#fff5f5");
    }

    @Test
    @DisplayName("renderHtml() prepends warning indicator to REVERSAL_WATCH headline")
    void renderHtml_reversalWatchSection_prependsWarningIndicator() {
        String html = renderer.renderHtml(draftWithReversalWatch());
        assertThat(html).contains("\u26A0");
    }

    @Test
    @DisplayName("renderPlainText() uses distinct separator for REVERSAL_WATCH section")
    void renderPlainText_reversalWatchSection_usesDistinctSeparator() {
        String text = renderer.renderPlainText(draftWithReversalWatch());
        assertThat(text).contains("=== REVERSAL WATCH ===");
        assertThat(text).doesNotContain("---\nReversal Watch");
    }
}

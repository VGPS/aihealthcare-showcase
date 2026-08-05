package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DigestNewsletterRenderer}.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-07-20
 * @updated 2026-08-05
 */
class DigestNewsletterRendererTest {

    private ArticleIngestionPort articleIngestionPort;
    private DigestNewsletterRenderer renderer;

    @BeforeEach
    void setUp() {
        articleIngestionPort = mock(ArticleIngestionPort.class);
        renderer = new DigestNewsletterRenderer(articleIngestionPort);
    }

    @Test
    void buildDigest_withArticles_wrapsInEmailLayout() {
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of(
                        makeArticle("AI Diagnoses Cancer Early", "https://example.com/cancer", "Great article body"),
                        makeArticle("New FDA Approval for AI", "https://example.com/fda", "FDA approved a new device")
                ));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("News Articles From");
        assertThat(result.get().htmlContent()).contains("AI Diagnoses Cancer Early");
        assertThat(result.get().htmlContent()).contains("New FDA Approval for AI");
        assertThat(result.get().htmlContent()).contains("Upgrade to Subscriber");
        assertThat(result.get().status()).isEqualTo(NewsletterRunStatus.DRAFT);
    }

    @Test
    void buildDigest_containsCtaAndFooterLinks() {
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of(makeArticle("Test Article", "https://example.com/test", "Body")));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("Want deeper AI analysis?");
        assertThat(result.get().htmlContent()).contains("$39/mo");
        assertThat(result.get().htmlContent()).contains("pricing");
        assertThat(result.get().htmlContent()).contains("Free 7 Day Demo");
        assertThat(result.get().htmlContent()).contains("app.bigskylabs.ai/demo");
        assertThat(result.get().htmlContent()).contains("Unsubscribe");
        assertThat(result.get().htmlContent()).contains("app.bigskylabs.ai/unsubscribe");
    }

    @Test
    void buildDigest_noArticles_returnsEmpty() {
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of());

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isEmpty();
    }

    @Test
    void buildDigest_deduplicatesByTitle() {
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of(
                        makeArticle("Same Title", "https://example.com/a", "Body A"),
                        makeArticle("Same Title", "https://example.com/b", "Body B"),
                        makeArticle("Different Title", "https://example.com/c", "Body C")
                ));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().title()).startsWith("2 News Articles From");
    }

    @Test
    void buildDigest_filtersNonsenseTitles() {
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of(
                        makeArticle("https://perplexity.ai/search/abc123", "https://example.com/a", "Body"),
                        makeArticle("www.example.com/page", "https://example.com/b", "Body"),
                        makeArticle("Real AI Healthcare Article", "https://example.com/c", "Body")
                ));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().title()).startsWith("1 News Articles From");
        assertThat(result.get().htmlContent()).contains("Real AI Healthcare Article");
        assertThat(result.get().htmlContent()).doesNotContain("perplexity.ai");
    }

    @Test
    void buildDigest_runIdContainsDate() {
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of(makeArticle("Test", "https://example.com/t", "Body")));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().runId()).startsWith("digest-");
        assertThat(result.get().title()).contains("News Articles From");
    }

    @Test
    void buildDigest_includesPlainTextVersion() {
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of(
                        makeArticle("AI in Surgery", "https://example.com/surgery", "Robots help surgeons"),
                        makeArticle("Telehealth Expansion", "https://example.com/telehealth", "Remote care grows")
                ));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().plainTextContent()).contains("AI in Surgery");
        assertThat(result.get().plainTextContent()).contains("example.com/surgery");
        assertThat(result.get().plainTextContent()).contains("Telehealth Expansion");
    }

    @Test
    void buildDigest_articleWithBodyPreview_showsInHtml() {
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of(makeArticle("Article With Body", "https://example.com/body", "This is the body text preview")));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("This is the body text preview");
    }

    @Test
    void buildDigest_articleWithAuthorAndDate_showsMeta() {
        NewsArticle article = new NewsArticle(
                "a1", "Authored Article", URI.create("https://example.com/authored"),
                "Body text", "AI Healthcare", "Dr. Smith", null,
                "PubMed", "ACADEMIC", 0.9, Instant.parse("2026-08-03T10:00:00Z")
        );
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of(article));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("Dr. Smith");
        assertThat(result.get().htmlContent()).contains("August 3, 2026");
    }

    @Test
    void buildDigest_stripsHtmlTagsAndEntitiesFromBody() {
        String htmlBody = "<p>AI is transforming&nbsp;healthcare.&amp; More&lt;details&gt; here.</p>";
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of(makeArticle("HTML Test", "https://example.com/html", htmlBody)));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("AI is transforming healthcare.&amp; More");
        assertThat(result.get().htmlContent()).doesNotContain("&nbsp;");
        assertThat(result.get().htmlContent()).doesNotContain("<p>");
    }

    @Test
    void buildDigest_bodyPreviewEndsAtSentenceBoundary() {
        String longBody = "First sentence about AI in healthcare. "
                + "Second sentence covers diagnostics and imaging. "
                + "Third sentence discusses regulatory frameworks and compliance requirements. "
                + "Fourth sentence about machine learning models and their applications in clinical settings. "
                + "Fifth sentence that goes well beyond the character limit and should be cut off.";
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of(makeArticle("Long Body", "https://example.com/long", longBody)));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains("First sentence about AI in healthcare.");
        assertThat(html).doesNotContain("...");
        assertThat(html).doesNotContain("Fifth sentence");
    }

    @Test
    void buildDigest_excludesCompetitorAndHuggingfaceTiers() {
        NewsArticle competitor = new NewsArticle(
                "c1", "Perplexity Homepage", URI.create("https://perplexity.ai"),
                "AI for the curious", "Competitor", null, null,
                "Perplexity", "COMPETITOR", 0.5, Instant.now()
        );
        NewsArticle huggingface = new NewsArticle(
                "h1", "HF Model XYZ", URI.create("https://huggingface.co/model"),
                "Model card", "HuggingFace", null, null,
                "HuggingFace", "HUGGINGFACE", 0.5, Instant.now()
        );
        NewsArticle realArticle = makeArticle("Real Healthcare Article", "https://example.com/real", "Real content");
        when(articleIngestionPort.fetchRecentArticles(eq(3)))
                .thenReturn(List.of(competitor, huggingface, realArticle));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().title()).startsWith("1 News Articles From");
        assertThat(result.get().htmlContent()).contains("Real Healthcare Article");
        assertThat(result.get().htmlContent()).doesNotContain("Perplexity Homepage");
        assertThat(result.get().htmlContent()).doesNotContain("HF Model XYZ");
    }

    private NewsArticle makeArticle(String title, String url, String body) {
        return new NewsArticle(
                "art-" + title.hashCode(), title, URI.create(url),
                body, "AI Healthcare", null, null,
                "TestSource", "INDUSTRY", 0.5, Instant.now()
        );
    }
}

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
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(
                        makeArticle("AI Diagnoses Cancer Early", "https://example.com/cancer", "Great article body"),
                        makeArticle("New FDA Approval for AI", "https://example.com/fda", "FDA approved a new device")
                ));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("AI Healthcare Intelligence");
        assertThat(result.get().htmlContent()).contains("AI Diagnoses Cancer Early");
        assertThat(result.get().htmlContent()).contains("New FDA Approval for AI");
        assertThat(result.get().htmlContent()).contains("Upgrade to Subscriber");
        assertThat(result.get().status()).isEqualTo(NewsletterRunStatus.DRAFT);
    }

    @Test
    void buildDigest_containsCtaAndFooterLinks() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(makeArticle("Test Article", "https://example.com/test", "Body")));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("Want deeper AI analysis?");
        assertThat(result.get().htmlContent()).contains("$19/mo");
        assertThat(result.get().htmlContent()).contains("pricing");
        assertThat(result.get().htmlContent()).contains("Free 7 Day Demo");
        assertThat(result.get().htmlContent()).contains("app.bigskylabs.ai/demo");
        assertThat(result.get().htmlContent()).contains("Unsubscribe");
        assertThat(result.get().htmlContent()).contains("app.bigskylabs.ai/unsubscribe");
    }

    @Test
    void buildDigest_noArticles_returnsEmpty() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of());

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isEmpty();
    }

    @Test
    void buildDigest_deduplicatesByTitle() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(
                        makeArticle("Same Title", "https://example.com/a", "Body A"),
                        makeArticle("Same Title", "https://example.com/b", "Body B"),
                        makeArticle("Different Title", "https://example.com/c", "Body C")
                ));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().title()).startsWith("AI Healthcare Intelligence");
    }

    @Test
    void buildDigest_filtersNonsenseTitles() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(
                        makeArticle("https://perplexity.ai/search/abc123", "https://example.com/a", "Body"),
                        makeArticle("www.example.com/page", "https://example.com/b", "Body"),
                        makeArticle("Real AI Healthcare Article", "https://example.com/c", "Body")
                ));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().title()).startsWith("AI Healthcare Intelligence");
        assertThat(result.get().htmlContent()).contains("Real AI Healthcare Article");
        assertThat(result.get().htmlContent()).doesNotContain("perplexity.ai");
    }

    @Test
    void buildDigest_runIdContainsDate() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(makeArticle("Test", "https://example.com/t", "Body")));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().runId()).startsWith("digest-");
        assertThat(result.get().title()).startsWith("AI Healthcare Intelligence");
    }

    @Test
    void buildDigest_includesPlainTextVersion() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
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
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
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
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(article));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("Dr. Smith");
        assertThat(result.get().htmlContent()).contains("August 3, 2026");
    }

    @Test
    void buildDigest_stripsHtmlTagsAndEntitiesFromBody() {
        String htmlBody = "<p>AI is transforming&nbsp;healthcare.&amp; More&lt;details&gt; here.</p>";
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(makeArticle("HTML Test", "https://example.com/html", htmlBody)));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("AI is transforming healthcare.");
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
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(makeArticle("Long Body", "https://example.com/long", longBody)));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains("First sentence about AI in healthcare.");
        assertThat(html).doesNotContain("...");
        assertThat(html).doesNotContain("Fifth sentence");
    }

    @Test
    void buildDigest_excludesCompetitorTier_allowsResearch() {
        NewsArticle competitor = new NewsArticle(
                "c1", "Perplexity Homepage", URI.create("https://perplexity.ai"),
                "AI for the curious", "Competitor", null, null,
                "Perplexity", "COMPETITOR", 0.5, Instant.now()
        );
        NewsArticle research = new NewsArticle(
                "h1", "HF Model XYZ", URI.create("https://huggingface.co/model"),
                "Model card for healthcare LLM", "HuggingFace", null, null,
                "HuggingFace", "RESEARCH", 0.75, Instant.now()
        );
        NewsArticle realArticle = makeArticle("Real Healthcare Article", "https://example.com/real", "Real content");
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(competitor, research, realArticle));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        // COMPETITOR excluded; RESEARCH passes through — 2 articles
        assertThat(result.get().title()).startsWith("AI Healthcare Intelligence");
        assertThat(result.get().htmlContent()).contains("Real Healthcare Article");
        assertThat(result.get().htmlContent()).contains("HF Model XYZ");
        assertThat(result.get().htmlContent()).doesNotContain("Perplexity Homepage");
    }

    @Test
    void buildDigest_partitionsIntoTodaysAndDiscovered() {
        NewsArticle todayArticle = new NewsArticle(
                "t1", "Breaking: AI Clears FDA", URI.create("https://example.com/fda"),
                "FDA clears new AI device", "AI Healthcare", null, null,
                "TestSource", "INDUSTRY", 0.5, Instant.now()
        );
        NewsArticle oldArticle = new NewsArticle(
                "o1", "Last Week's Discovery", URI.create("https://example.com/old"),
                "Older article body text", "AI Healthcare", null, null,
                "TestSource", "INDUSTRY", 0.5, Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS)
        );
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(todayArticle, oldArticle));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains("Today&rsquo;s Intelligence");
        assertThat(html).contains("Also Discovered");
        int todayPos = html.indexOf("Today&rsquo;s");
        int discoveredPos = html.indexOf("Also Discovered");
        assertThat(todayPos).isLessThan(discoveredPos);
    }

    @Test
    void buildDigest_allTodayArticles_noDiscoveredSection() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(makeArticle("Fresh Article", "https://example.com/fresh", "Fresh body")));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains("Today&rsquo;s Intelligence");
        assertThat(html).doesNotContain("Also Discovered");
    }

    @Test
    void buildDigest_plainTextHasSections() {
        NewsArticle todayArticle = makeArticle("New Today", "https://example.com/new", "Fresh news");
        NewsArticle oldArticle = new NewsArticle(
                "o2", "Old Finding", URI.create("https://example.com/old2"),
                "Older finding body", "AI Healthcare", null, null,
                "TestSource", "INDUSTRY", 0.5, Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS)
        );
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(todayArticle, oldArticle));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String plain = result.get().plainTextContent();
        assertThat(plain).contains("TODAY'S INTELLIGENCE");
        assertThat(plain).contains("ALSO DISCOVERED");
    }

    private NewsArticle makeArticle(String title, String url, String body) {
        return new NewsArticle(
                "art-" + title.hashCode(), title, URI.create(url),
                body, "AI Healthcare", null, null,
                "TestSource", "INDUSTRY", 0.5, Instant.now()
        );
    }
}

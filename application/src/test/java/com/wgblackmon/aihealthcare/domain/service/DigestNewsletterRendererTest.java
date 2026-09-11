package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import com.wgblackmon.aihealthcare.domain.model.ScoredArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleBodyFormattingPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleScoringPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DigestNewsletterRenderer}.
 *
 * <p>Scoring and formatting ports are mocked; by default scoring returns an empty
 * list so tests that do not exercise the Article of the Day feature see the same
 * output as the pre-scoring implementation.
 *
 * @author  Bill Blackmon
 * @version 3.1
 * @since   2026-07-20
 * @updated 2026-09-11
 */
class DigestNewsletterRendererTest {

    private ArticleIngestionPort     articleIngestionPort;
    private ArticleScoringPort       articleScoringPort;
    private ArticleBodyFormattingPort bodyFormattingPort;
    private DigestNewsletterRenderer renderer;

    @BeforeEach
    void setUp() {
        articleIngestionPort = mock(ArticleIngestionPort.class);
        articleScoringPort   = mock(ArticleScoringPort.class);
        bodyFormattingPort   = mock(ArticleBodyFormattingPort.class);

        // Default: scoring returns empty (graceful degradation)
        when(articleScoringPort.scoreArticles(any(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of());
        // Default: formatting returns empty
        when(bodyFormattingPort.formatWithEntityBolding(anyString(), anyString()))
                .thenReturn("");

        renderer = new DigestNewsletterRenderer(
                articleIngestionPort, articleScoringPort, bodyFormattingPort,
                new ArticleQualityFilter());
    }

    // -------------------------------------------------------------------------
    // Existing digest layout tests (unchanged behaviour with empty scoring)
    // -------------------------------------------------------------------------

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
        assertThat(result.get().htmlContent()).contains("Enterprise plans");
        assertThat(result.get().htmlContent()).contains("pricing#enterprise");
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
    void buildDigest_bodyPreviewRedundantWithTitle_omitsPreview() {
        String title = "FDA Seeks Public Feedback on Regulatory Approach for Generative AI-Enabled Medical Devices - The National Law Review";
        String body  = "FDA Seeks Public Feedback on Regulatory Approach for Generative AI-Enabled Medical Devices The National Law Review";
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(makeArticle(title, "https://example.com/fda-feedback", body)));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains(title);
        // Title appears in article card + Sources section = 2; redundant body preview must not add a third
        long titleOccurrences = html.split(java.util.regex.Pattern.quote(title.substring(0, 40)), -1).length - 1;
        assertThat(titleOccurrences).isEqualTo(2);
    }

    @Test
    void buildDigest_bodyPreviewAddsNewContent_stillShowsPreview() {
        String title = "FDA Clears New AI Device";
        String body  = "FDA Clears New AI Device. The clearance covers real-time diagnostic imaging analysis for radiology departments nationwide.";
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(makeArticle(title, "https://example.com/fda-device", body)));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("real-time diagnostic imaging analysis");
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
        assertThat(html.indexOf("Today&rsquo;s")).isLessThan(html.indexOf("Also Discovered"));
    }

    @Test
    void buildDigest_allTodayArticles_noDiscoveredSection() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(makeArticle("Fresh Article", "https://example.com/fresh", "Fresh body")));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("Today&rsquo;s Intelligence");
        assertThat(result.get().htmlContent()).doesNotContain("Also Discovered");
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

    // -------------------------------------------------------------------------
    // Citation numbering and Sources section
    // -------------------------------------------------------------------------

    @Test
    void buildDigest_articleCardsHaveNumberedCitations() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(
                        makeArticle("First Article", "https://example.com/first", "Body one"),
                        makeArticle("Second Article", "https://example.com/second", "Body two")
                ));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains("[1]");
        assertThat(html).contains("[2]");
        assertThat(html).contains("#source-1");
        assertThat(html).contains("#source-2");
    }

    @Test
    void buildDigest_sourceSectionPresent_withNumberedEntries() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(
                        makeArticle("Alpha Article", "https://example.com/alpha", "Alpha body"),
                        makeArticle("Beta Article", "https://example.com/beta", "Beta body")
                ));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains("Sources");
        assertThat(html).contains("id=\"source-1\"");
        assertThat(html).contains("id=\"source-2\"");
        assertThat(html).contains("example.com/alpha");
        assertThat(html).contains("example.com/beta");
    }

    @Test
    void buildDigest_sourceSectionShowsSourceName() {
        NewsArticle article = new NewsArticle(
                "a1", "Named Source Article", URI.create("https://example.com/named"),
                "Body", "AI Healthcare", null, null,
                "Reuters Health", "INDUSTRY", 0.8, Instant.now()
        );
        when(articleIngestionPort.fetchRecentArticles(eq(1))).thenReturn(List.of(article));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("Reuters Health");
    }

    @Test
    void buildDigest_plainTextHasSourcesSection() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(
                        makeArticle("Plain Source Test", "https://example.com/plain", "Body text")
                ));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String plain = result.get().plainTextContent();
        assertThat(plain).contains("=== SOURCES ===");
        assertThat(plain).contains("[1] Plain Source Test");
        assertThat(plain).contains("example.com/plain");
    }

    @Test
    void buildDigest_continuousNumberingAcrossSections() {
        NewsArticle todayArticle = new NewsArticle(
                "t1", "Today First", URI.create("https://example.com/today"),
                "Today body", "AI Healthcare", null, null,
                "TestSource", "INDUSTRY", 0.5, Instant.now()
        );
        NewsArticle oldArticle = new NewsArticle(
                "o1", "Old Finding", URI.create("https://example.com/old"),
                "Old body", "AI Healthcare", null, null,
                "TestSource", "INDUSTRY", 0.5, Instant.now().minus(5, java.time.temporal.ChronoUnit.DAYS)
        );
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(todayArticle, oldArticle));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        // Today's article is [1], old article is [2]
        assertThat(html).contains("[1]");
        assertThat(html).contains("[2]");
        assertThat(html).contains("id=\"source-1\"");
        assertThat(html).contains("id=\"source-2\"");
    }

    // -------------------------------------------------------------------------
    // Article of the Day tests (CI-31)
    // -------------------------------------------------------------------------

    @Test
    void buildDigest_withTopScoredArticle_rendersFeaturedBlock() {
        NewsArticle article = makeArticle("FDA Clears First AI Surgical Robot",
                "https://example.com/fda-robot", "Landmark approval for AI robotic surgery.");
        when(articleIngestionPort.fetchRecentArticles(eq(1))).thenReturn(List.of(article));

        ScoredArticle topScore = new ScoredArticle(
                "art-" + "FDA Clears First AI Surgical Robot".hashCode(),
                "FDA Clears First AI Surgical Robot",
                9, "First-of-kind FDA clearance for an AI-guided robotic surgical system.",
                "AI Healthcare");
        when(articleScoringPort.scoreArticles(any(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of(topScore));
        when(bodyFormattingPort.formatWithEntityBolding(anyString(), anyString()))
                .thenReturn("The **FDA** cleared the first **AI**-guided robotic surgical system.\n\nThis marks a landmark moment for **robotic surgery**.");

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains("Article of the Day");
        assertThat(html).contains("9/10");
        assertThat(html).contains("First-of-kind FDA clearance");
        assertThat(html).contains("<strong>FDA</strong>");
        assertThat(html).contains("padding-left:16px");
    }

    @Test
    void buildDigest_featuredBlockAppearsBeforeTodaysIntelligence() {
        NewsArticle article = makeArticle("Top AI Article", "https://example.com/top", "Significant finding.");
        when(articleIngestionPort.fetchRecentArticles(eq(1))).thenReturn(List.of(article));

        ScoredArticle topScore = new ScoredArticle(
                "art-" + "Top AI Article".hashCode(), "Top AI Article",
                8, "Significant clinical milestone.", "AI Healthcare");
        when(articleScoringPort.scoreArticles(any(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of(topScore));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        int featuredPos = html.indexOf("Article of the Day");
        int todaysPos   = html.indexOf("Today&rsquo;s Intelligence");
        assertThat(featuredPos).isGreaterThan(-1);
        assertThat(todaysPos).isGreaterThan(-1);
        assertThat(featuredPos).isLessThan(todaysPos);
    }

    @Test
    void buildDigest_scoringFails_digestSendsWithoutFeaturedBlock() {
        when(articleIngestionPort.fetchRecentArticles(eq(1)))
                .thenReturn(List.of(makeArticle("Normal Article", "https://example.com/normal", "Body text")));
        when(articleScoringPort.scoreArticles(any(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("LLM timeout"));

        // Should not throw; digest sends without Article of the Day
        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).doesNotContain("Article of the Day");
        assertThat(result.get().htmlContent()).contains("Normal Article");
    }

    @Test
    void buildDigest_formattingFails_usesBodyPreviewFallback() {
        NewsArticle article = makeArticle("Important Finding",
                "https://example.com/finding", "AI detects early-stage cancer.");
        when(articleIngestionPort.fetchRecentArticles(eq(1))).thenReturn(List.of(article));

        ScoredArticle topScore = new ScoredArticle(
                "art-" + "Important Finding".hashCode(), "Important Finding",
                8, "Concrete clinical outcome.", "AI Healthcare");
        when(articleScoringPort.scoreArticles(any(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of(topScore));
        when(bodyFormattingPort.formatWithEntityBolding(anyString(), anyString()))
                .thenThrow(new RuntimeException("formatting unavailable"));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains("Article of the Day");
        // Falls back to raw body preview
        assertThat(html).contains("AI detects early-stage cancer.");
    }

    @Test
    void buildDigest_featuredPlainTextIncludesRationale() {
        NewsArticle article = makeArticle("Landmark Study", "https://example.com/study", "Breakthrough results.");
        when(articleIngestionPort.fetchRecentArticles(eq(1))).thenReturn(List.of(article));

        ScoredArticle topScore = new ScoredArticle(
                "art-" + "Landmark Study".hashCode(), "Landmark Study",
                9, "First-ever RCT showing AI outperforms radiologists.", "AI Healthcare");
        when(articleScoringPort.scoreArticles(any(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of(topScore));

        Optional<NewsletterRun> result = renderer.buildDigest();

        assertThat(result).isPresent();
        String plain = result.get().plainTextContent();
        assertThat(plain).contains("ARTICLE OF THE DAY");
        assertThat(plain).contains("9/10");
        assertThat(plain).contains("First-ever RCT");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private NewsArticle makeArticle(String title, String url, String body) {
        return new NewsArticle(
                "art-" + title.hashCode(), title, URI.create(url),
                body, "AI Healthcare", null, null,
                "TestSource", "INDUSTRY", 0.5, Instant.now()
        );
    }
}

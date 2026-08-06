package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterRun;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SampleNewsletterRenderer}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
@ExtendWith(MockitoExtension.class)
class SampleNewsletterRendererTest {

    @Mock
    private ArticleIngestionPort articleIngestionPort;

    private SampleNewsletterRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new SampleNewsletterRenderer(articleIngestionPort);
    }

    private NewsArticle article(String id, String title) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                "Body text for " + title, "Test Topic", "Author",
                null, "Source", "INDUSTRY", 0.5, Instant.now());
    }

    @Test
    void buildSample_noArticles_returnsEmpty() {
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(List.of());

        Optional<NewsletterRun> result = renderer.buildSample();

        assertThat(result).isEmpty();
    }

    @Test
    void buildSample_withArticles_returnsRun() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "AI startup raises $100M"));
        articles.add(article("a2", "FDA clears new AI device"));
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(articles);

        Optional<NewsletterRun> result = renderer.buildSample();

        assertThat(result).isPresent();
        assertThat(result.get().runId()).startsWith("sample-");
        assertThat(result.get().title()).contains("Complimentary Issue");
    }

    @Test
    void buildSample_htmlContainsTrialCta() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Healthcare AI trends"));
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(articles);

        Optional<NewsletterRun> result = renderer.buildSample();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains("Start Your Free 7-Day Trial");
        assertThat(html).contains("app.bigskylabs.ai/register");
        assertThat(html).contains("Complimentary Issue");
    }

    @Test
    void buildSample_htmlContainsSubscriberBenefits() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "AI diagnostic tool"));
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(articles);

        Optional<NewsletterRun> result = renderer.buildSample();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains("Subscribers Also Get");
        assertThat(html).contains("deal signals");
        assertThat(html).contains("regulatory alerts");
    }

    @Test
    void buildSample_htmlContainsArticleContent() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Tempus AI announces partnership"));
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(articles);

        Optional<NewsletterRun> result = renderer.buildSample();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).contains("Tempus AI announces partnership");
        assertThat(html).contains("example.com/a1");
    }

    @Test
    void buildSample_plainTextIncludesTrialLink() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "AI in healthcare"));
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(articles);

        Optional<NewsletterRun> result = renderer.buildSample();

        assertThat(result).isPresent();
        String plain = result.get().plainTextContent();
        assertThat(plain).contains("COMPLIMENTARY ISSUE");
        assertThat(plain).contains("app.bigskylabs.ai/register");
    }

    @Test
    void buildSample_filtersDuplicateTitles() {
        List<NewsArticle> articles = new ArrayList<>();
        NewsArticle dup1 = new NewsArticle("a1", "Duplicated Headline Here",
                URI.create("https://example.com/a1"), "Some body text.", "Test Topic",
                "Author", null, "Source", "INDUSTRY", 0.5, Instant.now());
        NewsArticle dup2 = new NewsArticle("a2", "Duplicated Headline Here",
                URI.create("https://example.com/a2"), "Other body text.", "Test Topic",
                "Author", null, "Source", "INDUSTRY", 0.5, Instant.now());
        articles.add(dup1);
        articles.add(dup2);
        articles.add(article("a3", "Different Title"));
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(articles);

        Optional<NewsletterRun> result = renderer.buildSample();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        int count = countOccurrences(html, "Duplicated Headline Here");
        assertThat(count).isEqualTo(1);
    }

    @Test
    void buildSample_filtersNonsenseTitles() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "https://example.com/nonsense"));
        articles.add(article("a2", "Real Article Title"));
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(articles);

        Optional<NewsletterRun> result = renderer.buildSample();

        assertThat(result).isPresent();
        String html = result.get().htmlContent();
        assertThat(html).doesNotContain("nonsense");
        assertThat(html).contains("Real Article Title");
    }

    @Test
    void buildSample_containsUnsubscribePlaceholder() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Article"));
        when(articleIngestionPort.fetchRecentArticles(anyInt())).thenReturn(articles);

        Optional<NewsletterRun> result = renderer.buildSample();

        assertThat(result).isPresent();
        assertThat(result.get().htmlContent()).contains("{{unsubscribe_url}}");
        assertThat(result.get().plainTextContent()).contains("{{unsubscribe_url}}");
    }

    private int countOccurrences(String text, String target) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(target, idx)) >= 0) {
            count++;
            idx += target.length();
        }
        return count;
    }
}

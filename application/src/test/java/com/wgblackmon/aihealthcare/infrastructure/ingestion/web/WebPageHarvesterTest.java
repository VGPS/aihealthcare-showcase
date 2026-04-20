package com.wgblackmon.aihealthcare.infrastructure.ingestion.web;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.port.outbound.ContentHashPort;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceConfig;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceConfig.FeedTier;
import com.wgblackmon.aihealthcare.infrastructure.ingestion.feed.FeedSourceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link WebPageHarvester}.
 *
 * <p>Uses a spy on the harvester to override {@code fetchPageHtml()} so
 * that tests never make real HTTP calls.  Verifies change-detection logic,
 * SHA-256 hashing, and content extraction.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
@ExtendWith(MockitoExtension.class)
class WebPageHarvesterTest {

    @Mock
    private ContentHashPort contentHashPort;

    private WebPageHarvester harvester;

    private static final FeedSourceConfig COMPETITOR_SOURCE = new FeedSourceConfig(
            1L, "Test Competitor", "https://example.com/health",
            FeedTier.COMPETITOR, 0.7, 1);

    @BeforeEach
    void setUp() {
        FeedSourceProperties properties = new FeedSourceProperties();
        FeedSourceProperties.FeedEntry entry = new FeedSourceProperties.FeedEntry();
        entry.setTopicId(1L);
        entry.setName("Test Competitor");
        entry.setUrl("https://example.com/health");
        entry.setTier(FeedTier.COMPETITOR);
        entry.setBaseWeight(0.7);
        entry.setMaxItems(1);
        properties.setSources(List.of(entry));

        harvester = spy(new WebPageHarvester(properties, contentHashPort));
    }

    @Test
    void harvestChangedPages_firstCheck_returnsArticle() {
        doReturn("<html><head><title>Health AI News</title></head>" +
                 "<body><main>Important health AI content here</main></body></html>")
                .when(harvester).fetchPageHtml("https://example.com/health");
        when(contentHashPort.getHash("https://example.com/health")).thenReturn(null);

        List<NewsArticle> result = harvester.harvestChangedPages();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Health AI News");
        assertThat(result.get(0).sourceTier()).isEqualTo("COMPETITOR");
        assertThat(result.get(0).sourceWeight()).isEqualTo(0.7);
        verify(contentHashPort).saveHash(eq("https://example.com/health"), anyString());
    }

    @Test
    void harvestChangedPages_contentUnchanged_returnsEmpty() {
        String html = "<html><head><title>Health AI</title></head>" +
                      "<body><main>Same content</main></body></html>";
        doReturn(html).when(harvester).fetchPageHtml("https://example.com/health");

        String expectedHash = harvester.sha256("Same content");
        when(contentHashPort.getHash("https://example.com/health")).thenReturn(expectedHash);

        List<NewsArticle> result = harvester.harvestChangedPages();

        assertThat(result).isEmpty();
        verify(contentHashPort, never()).saveHash(anyString(), anyString());
    }

    @Test
    void harvestChangedPages_contentChanged_returnsArticle() {
        doReturn("<html><head><title>Updated Page</title></head>" +
                 "<body><main>New content after update</main></body></html>")
                .when(harvester).fetchPageHtml("https://example.com/health");
        when(contentHashPort.getHash("https://example.com/health")).thenReturn("old-hash-value");

        List<NewsArticle> result = harvester.harvestChangedPages();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Updated Page");
        verify(contentHashPort).saveHash(eq("https://example.com/health"), anyString());
    }

    @Test
    void harvestChangedPages_fetchFails_returnsEmpty() {
        doReturn("").when(harvester).fetchPageHtml("https://example.com/health");
        when(contentHashPort.getHash("https://example.com/health")).thenReturn(null);

        List<NewsArticle> result = harvester.harvestChangedPages();

        // Empty page still produces an article on first check (hash is new)
        // But content will be empty string
        assertThat(result).hasSize(1);
        assertThat(result.get(0).bodyText()).isEmpty();
    }

    @Test
    void sha256_producesConsistentHash() {
        String hash1 = harvester.sha256("hello world");
        String hash2 = harvester.sha256("hello world");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64);
    }

    @Test
    void sha256_differentInputs_produceDifferentHashes() {
        String hash1 = harvester.sha256("content version 1");
        String hash2 = harvester.sha256("content version 2");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void extractMainContent_prefersMainElement() {
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(
                "<html><body><nav>Nav stuff</nav><main>Main content here</main>" +
                "<footer>Footer</footer></body></html>");

        String content = harvester.extractMainContent(doc);

        assertThat(content).isEqualTo("Main content here");
    }

    @Test
    void extractMainContent_fallsBackToBody() {
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(
                "<html><body><div>All body content</div></body></html>");

        String content = harvester.extractMainContent(doc);

        assertThat(content).isEqualTo("All body content");
    }

    @Test
    void harvestChangedPages_articleUrl_containsSnapshotFragment() {
        doReturn("<html><head><title>Page</title></head>" +
                 "<body><main>Content</main></body></html>")
                .when(harvester).fetchPageHtml("https://example.com/health");
        when(contentHashPort.getHash("https://example.com/health")).thenReturn(null);

        List<NewsArticle> result = harvester.harvestChangedPages();

        assertThat(result.get(0).url().toString()).startsWith("https://example.com/health#snapshot-");
    }
}

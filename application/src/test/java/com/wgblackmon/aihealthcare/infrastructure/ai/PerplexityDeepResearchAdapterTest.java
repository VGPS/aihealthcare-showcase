package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PerplexityDeepResearchAdapter}.
 *
 * <p>Tests the adapter's availability check, prompt building, and the critical
 * {@code <think>} block stripping logic. HTTP interaction tests are separate
 * (would use WireMock for full integration).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-28
 * @updated 2026-07-28
 */
class PerplexityDeepResearchAdapterTest {

    @Test
    void isAvailable_returnsFalse_whenApiKeyBlank() {
        PerplexityDeepResearchAdapter adapter = new PerplexityDeepResearchAdapter(
                "", 1000, 5000, null);
        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_returnsFalse_whenApiKeyNull() {
        PerplexityDeepResearchAdapter adapter = new PerplexityDeepResearchAdapter(
                null, 1000, 5000, null);
        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_returnsFalse_whenPlaceholder() {
        PerplexityDeepResearchAdapter adapter = new PerplexityDeepResearchAdapter(
                "placeholder-set-PERPLEXITY_API_KEY", 1000, 5000, null);
        assertThat(adapter.isAvailable()).isFalse();
    }

    @Test
    void isAvailable_returnsTrue_whenApiKeySet() {
        PerplexityDeepResearchAdapter adapter = new PerplexityDeepResearchAdapter(
                "pplx-abc123", 1000, 5000, null);
        assertThat(adapter.isAvailable()).isTrue();
    }

    @Test
    void generateSummary_returnsNull_whenNotAvailable() {
        PerplexityDeepResearchAdapter adapter = new PerplexityDeepResearchAdapter(
                "", 1000, 5000, null);

        NewsArticle article = new NewsArticle("a1", "Test", URI.create("https://example.com"),
                "body", "Topic", null, null, "Source", "INDUSTRY", 0.5, Instant.now());

        String result = adapter.generateSummary("radiology", List.of(article));

        assertThat(result).isNull();
    }

    @Test
    void stripThinkBlocks_removesLeadingThinkBlock() {
        String content = "<think>Internal reasoning about the query and planning steps.</think>\n\n" +
                "The healthcare AI radiology market is experiencing rapid growth.";

        String result = PerplexityDeepResearchAdapter.stripThinkBlocks(content);

        assertThat(result).isEqualTo("The healthcare AI radiology market is experiencing rapid growth.");
    }

    @Test
    void stripThinkBlocks_removesMultipleThinkBlocks() {
        String content = "<think>First reasoning block.\nLine 2.</think>\n" +
                "Paragraph one.\n" +
                "<think>Another think block.</think>\n" +
                "Paragraph two.";

        String result = PerplexityDeepResearchAdapter.stripThinkBlocks(content);

        assertThat(result).isEqualTo("Paragraph one.\nParagraph two.");
    }

    @Test
    void stripThinkBlocks_preservesContentWithoutThinkBlocks() {
        String content = "Clean content with no think blocks.";

        String result = PerplexityDeepResearchAdapter.stripThinkBlocks(content);

        assertThat(result).isEqualTo("Clean content with no think blocks.");
    }

    @Test
    void stripThinkBlocks_handlesNull() {
        assertThat(PerplexityDeepResearchAdapter.stripThinkBlocks(null)).isNull();
    }

    @Test
    void buildPrompt_includesKeywordAndArticles() {
        PerplexityDeepResearchAdapter adapter = new PerplexityDeepResearchAdapter(
                "pplx-abc123", 1000, 5000, null);

        NewsArticle article = new NewsArticle("a1", "FDA clears AI radiology tool",
                URI.create("https://example.com"), "body", "Topic",
                null, null, "MedCity News", "INDUSTRY", 0.5, Instant.now());

        String prompt = adapter.buildPrompt("radiology ai", List.of(article));

        assertThat(prompt).contains("radiology ai");
        assertThat(prompt).contains("FDA clears AI radiology tool");
        assertThat(prompt).contains("MedCity News");
        assertThat(prompt).contains("executive brief");
    }

    @Test
    void buildPrompt_handlesEmptyArticles() {
        PerplexityDeepResearchAdapter adapter = new PerplexityDeepResearchAdapter(
                "pplx-abc123", 1000, 5000, null);

        String prompt = adapter.buildPrompt("genomics", List.of());

        assertThat(prompt).contains("genomics");
        assertThat(prompt).doesNotContain("recent articles");
    }
}

package com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig;
import com.wgblackmon.aihealthcare.domain.port.outbound.SearchPromptPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PerplexityHarvester}.
 *
 * <p>Verifies that the stub returns an empty list under all expected conditions
 * without making real HTTP calls.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-28
 * @updated 2026-04-28
 */
@ExtendWith(MockitoExtension.class)
class PerplexityHarvesterTest {

    @Mock
    private SearchPromptPort searchPromptPort;

    private static final SearchPromptConfig ACTIVE_PROMPT = new SearchPromptConfig(
            "PERPLEXITY", "Perplexity Deep Research",
            "Find substantive content on {topic} for an AI healthcare newsletter.",
            "Deep research.", true);

    private static final SearchPromptConfig INACTIVE_PROMPT = new SearchPromptConfig(
            "PERPLEXITY", "Perplexity Deep Research",
            "Find substantive content on {topic}.",
            "Inactive.", false);

    @Test
    void harvestArticles_noApiKey_returnsEmpty() {
        PerplexityHarvester harvester = new PerplexityHarvester("", searchPromptPort);

        List<NewsArticle> result = harvester.harvestArticles("AI diagnostics");

        assertThat(result).isEmpty();
        verify(searchPromptPort, never()).findByEngine(eq("PERPLEXITY"));
    }

    @Test
    void harvestArticles_apiKeyPresent_noActivePrompt_returnsEmpty() {
        when(searchPromptPort.findByEngine("PERPLEXITY")).thenReturn(Optional.empty());
        PerplexityHarvester harvester = new PerplexityHarvester("sk-test-key", searchPromptPort);

        List<NewsArticle> result = harvester.harvestArticles("AI diagnostics");

        assertThat(result).isEmpty();
    }

    @Test
    void harvestArticles_apiKeyPresent_inactivePrompt_returnsEmpty() {
        when(searchPromptPort.findByEngine("PERPLEXITY")).thenReturn(Optional.of(INACTIVE_PROMPT));
        PerplexityHarvester harvester = new PerplexityHarvester("sk-test-key", searchPromptPort);

        List<NewsArticle> result = harvester.harvestArticles("AI diagnostics");

        assertThat(result).isEmpty();
    }

    @Test
    void harvestArticles_apiKeyPresent_activePrompt_returnsEmptyStub() {
        when(searchPromptPort.findByEngine("PERPLEXITY")).thenReturn(Optional.of(ACTIVE_PROMPT));
        PerplexityHarvester harvester = new PerplexityHarvester("sk-test-key", searchPromptPort);

        List<NewsArticle> result = harvester.harvestArticles("clinical AI");

        // Stub always returns empty — full integration deferred
        assertThat(result).isEmpty();
        verify(searchPromptPort).findByEngine("PERPLEXITY");
    }

    @Test
    void harvestArticles_nullTopic_usesDefaultTopic() {
        when(searchPromptPort.findByEngine("PERPLEXITY")).thenReturn(Optional.of(ACTIVE_PROMPT));
        PerplexityHarvester harvester = new PerplexityHarvester("sk-test-key", searchPromptPort);

        List<NewsArticle> result = harvester.harvestArticles(null);

        assertThat(result).isEmpty();
    }
}

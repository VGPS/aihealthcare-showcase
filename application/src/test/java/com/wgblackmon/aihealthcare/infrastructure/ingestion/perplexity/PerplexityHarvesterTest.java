package com.wgblackmon.aihealthcare.infrastructure.ingestion.perplexity;

import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.SearchPromptConfig;
import com.wgblackmon.aihealthcare.domain.port.outbound.SearchPromptPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * Unit tests for {@link PerplexityHarvester}.
 *
 * <p>Guard-clause tests use an empty API key or missing prompt — no HTTP call is made.
 * The live-call path is tested via an explicit mock chain over {@link RestClient}'s
 * fluent API, returning a canned {@link PerplexityApiResponse} without touching the network.
 *
 * @author  Bill Blackmon
 * @version 2.0
 * @since   2026-04-28
 * @updated 2026-05-06
 */
@ExtendWith(MockitoExtension.class)
class PerplexityHarvesterTest {

    @Mock private SearchPromptPort searchPromptPort;
    @Mock private RestClient restClient;
    @Mock private RestClient.RequestBodyUriSpec bodyUriSpec;
    @Mock private RestClient.RequestBodySpec bodySpec;
    @Mock private RestClient.ResponseSpec responseSpec;

    private static final SearchPromptConfig ACTIVE_PROMPT = new SearchPromptConfig(
            "PERPLEXITY", "Perplexity Deep Research",
            "Find substantive content on {topic} for an AI healthcare newsletter.",
            "Deep research.", true);

    private static final SearchPromptConfig INACTIVE_PROMPT = new SearchPromptConfig(
            "PERPLEXITY", "Perplexity Deep Research",
            "Find substantive content on {topic}.",
            "Inactive.", false);

    // -------------------------------------------------------------------------
    // Guard clauses — no HTTP call expected
    // -------------------------------------------------------------------------

    @Test
    void harvestArticles_noApiKey_returnsEmpty() {
        PerplexityHarvester harvester = new PerplexityHarvester(searchPromptPort, "", restClient);

        List<NewsArticle> result = harvester.harvestArticles("AI diagnostics");

        assertThat(result).isEmpty();
        verify(searchPromptPort, never()).findByEngine(anyString());
    }

    @Test
    void harvestArticles_noActivePrompt_returnsEmpty() {
        when(searchPromptPort.findByEngine("PERPLEXITY")).thenReturn(Optional.empty());
        PerplexityHarvester harvester = new PerplexityHarvester(searchPromptPort, "real-key", restClient);

        List<NewsArticle> result = harvester.harvestArticles("AI diagnostics");

        assertThat(result).isEmpty();
    }

    @Test
    void harvestArticles_inactivePrompt_returnsEmpty() {
        when(searchPromptPort.findByEngine("PERPLEXITY")).thenReturn(Optional.of(INACTIVE_PROMPT));
        PerplexityHarvester harvester = new PerplexityHarvester(searchPromptPort, "real-key", restClient);

        List<NewsArticle> result = harvester.harvestArticles("AI diagnostics");

        assertThat(result).isEmpty();
    }

    @Test
    void harvestArticles_nullTopic_noApiKey_returnsEmpty() {
        PerplexityHarvester harvester = new PerplexityHarvester(searchPromptPort, "", restClient);

        List<NewsArticle> result = harvester.harvestArticles(null);

        assertThat(result).isEmpty();
    }

    @Test
    void harvestArticles_blankTopic_noApiKey_returnsEmpty() {
        PerplexityHarvester harvester = new PerplexityHarvester(searchPromptPort, "", restClient);

        List<NewsArticle> result = harvester.harvestArticles("   ");

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // Live-call path — RestClient stubbed via explicit mock chain
    // -------------------------------------------------------------------------

    private void stubRestClientChain(PerplexityApiResponse response) {
        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(PerplexityApiResponse.class)).thenReturn(response);
    }

    @Test
    void harvestArticles_successfulApiResponse_returnsArticles() {
        when(searchPromptPort.findByEngine("PERPLEXITY")).thenReturn(Optional.of(ACTIVE_PROMPT));

        PerplexityApiResponse fakeResponse = new PerplexityApiResponse(
                "id-1",
                List.of(new PerplexityApiResponse.PerplexityChoice(
                        new PerplexityApiResponse.PerplexityMessage(
                                "assistant",
                                "AI is transforming healthcare [1] through diagnostics. "
                                + "FDA cleared a new device [2] last month."))),
                List.of(
                        "https://pubmed.ncbi.nlm.nih.gov/article1",
                        "https://www.fda.gov/medical-devices/news"));
        stubRestClientChain(fakeResponse);

        PerplexityHarvester harvester = new PerplexityHarvester(searchPromptPort, "real-key", restClient);
        List<NewsArticle> result = harvester.harvestArticles("clinical AI");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).articleId()).isEqualTo("perplexity-1");
        assertThat(result.get(0).sourceTier()).isEqualTo("PERPLEXITY");
        assertThat(result.get(0).sourceName()).isEqualTo("Perplexity Sonar");
        assertThat(result.get(0).sourceWeight()).isEqualTo(0.85);
        assertThat(result.get(0).topic()).isEqualTo("clinical AI");
        assertThat(result.get(1).articleId()).isEqualTo("perplexity-2");
        assertThat(result.get(1).url().toString()).isEqualTo("https://www.fda.gov/medical-devices/news");
    }

    @Test
    void harvestArticles_apiResponseNoCitations_returnsEmpty() {
        when(searchPromptPort.findByEngine("PERPLEXITY")).thenReturn(Optional.of(ACTIVE_PROMPT));

        PerplexityApiResponse emptyResponse = new PerplexityApiResponse(
                "id-2",
                List.of(new PerplexityApiResponse.PerplexityChoice(
                        new PerplexityApiResponse.PerplexityMessage("assistant", "No specific sources."))),
                List.of());
        stubRestClientChain(emptyResponse);

        PerplexityHarvester harvester = new PerplexityHarvester(searchPromptPort, "real-key", restClient);
        List<NewsArticle> result = harvester.harvestArticles("clinical AI");

        assertThat(result).isEmpty();
    }

    @Test
    void harvestArticles_apiCallThrowsException_returnsEmpty() {
        when(searchPromptPort.findByEngine("PERPLEXITY")).thenReturn(Optional.of(ACTIVE_PROMPT));

        when(restClient.post()).thenReturn(bodyUriSpec);
        when(bodyUriSpec.uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any(String[].class))).thenReturn(bodySpec);
        when(bodySpec.contentType(any())).thenReturn(bodySpec);
        when(bodySpec.body(any(Object.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(PerplexityApiResponse.class))
                .thenThrow(new RuntimeException("connection refused"));

        PerplexityHarvester harvester = new PerplexityHarvester(searchPromptPort, "real-key", restClient);
        List<NewsArticle> result = harvester.harvestArticles("clinical AI");

        assertThat(result).isEmpty();
    }
}

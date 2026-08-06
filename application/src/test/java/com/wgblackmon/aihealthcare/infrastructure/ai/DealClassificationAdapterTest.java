package com.wgblackmon.aihealthcare.infrastructure.ai;

import com.wgblackmon.aihealthcare.domain.model.DealSignal;
import com.wgblackmon.aihealthcare.domain.model.DealSignalType;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DealClassificationAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-06
 * @updated 2026-08-06
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DealClassificationAdapterTest {

    @Mock
    private PromptLoaderService promptLoaderService;

    private DealClassificationAdapter adapter;

    @BeforeEach
    void setUp() {
        when(promptLoaderService.load("deal-classification.txt"))
                .thenReturn("Classify these articles:\n{numberedArticleList}");
        adapter = new TestDealClassificationAdapter(promptLoaderService);
    }

    private NewsArticle article(String id, String title, String body) {
        return new NewsArticle(id, title, URI.create("https://example.com/" + id),
                body, "Test", null, null, "Source", "INDUSTRY", 0.5, Instant.now());
    }

    @Test
    void buildPrompt_insertsArticles() {
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Tempus raises $200M", "Funding round details"));
        articles.add(article("a2", "Oracle acquires startup", "Acquisition news"));

        String prompt = adapter.buildPrompt(articles);

        assertThat(prompt).contains("[1] Tempus raises $200M");
        assertThat(prompt).contains("[2] Oracle acquires startup");
        assertThat(prompt).contains("Classify these articles:");
    }

    @Test
    void buildPrompt_truncatesLongBody() {
        String longBody = "x".repeat(600);
        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Title", longBody));

        String prompt = adapter.buildPrompt(articles);

        assertThat(prompt).contains("...");
        assertThat(prompt).doesNotContain("x".repeat(600));
    }

    @Test
    void parseResponse_validFunding_returnsSignal() {
        String response = "DEAL_RESULTS:\n"
                + "[1] TYPE: FUNDING | AMOUNT: $200M | COMPANY: Tempus AI | "
                + "COUNTERPARTY: SoftBank | CONFIDENCE: 0.92 | SUMMARY: Series D round | "
                + "ANALYSIS: Strong signal";

        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Tempus raises $200M", "details"));

        List<DealSignal> result = adapter.parseResponse(response, articles);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).signalType()).isEqualTo(DealSignalType.FUNDING);
        assertThat(result.get(0).dealAmount()).isEqualTo("$200M");
        assertThat(result.get(0).counterpartyName()).isEqualTo("SoftBank");
        assertThat(result.get(0).confidence()).isCloseTo(0.92, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void parseResponse_noDeal_skipsArticle() {
        String response = "DEAL_RESULTS:\n"
                + "[1] NO_DEAL\n"
                + "[2] TYPE: ACQUISITION | AMOUNT: $500M | COMPANY: BigCo | "
                + "COUNTERPARTY: StartupX | CONFIDENCE: 0.88 | SUMMARY: Acquisition | ANALYSIS: Note";

        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "General news", "No deal here"));
        articles.add(article("a2", "BigCo acquires StartupX", "deal details"));

        List<DealSignal> result = adapter.parseResponse(response, articles);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).signalType()).isEqualTo(DealSignalType.ACQUISITION);
    }

    @Test
    void parseResponse_emptyResponse_returnsEmptyList() {
        List<DealSignal> result = adapter.parseResponse("", List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void parseResponse_invalidIndex_skipsLine() {
        String response = "DEAL_RESULTS:\n"
                + "[99] TYPE: FUNDING | AMOUNT: $10M | COMPANY: Co | "
                + "COUNTERPARTY: N/A | CONFIDENCE: 0.7 | SUMMARY: Test | ANALYSIS: Test";

        List<NewsArticle> articles = new ArrayList<>();
        articles.add(article("a1", "Title", "Body"));

        List<DealSignal> result = adapter.parseResponse(response, articles);

        assertThat(result).isEmpty();
    }

    @Test
    void classifyDeals_emptyInput_returnsEmptyList() {
        List<DealSignal> result = adapter.classifyDeals(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void classifyDeals_nullInput_returnsEmptyList() {
        List<DealSignal> result = adapter.classifyDeals(null);
        assertThat(result).isEmpty();
    }

    /**
     * Test subclass that avoids real ChatClient creation.
     */
    private static class TestDealClassificationAdapter extends DealClassificationAdapter {

        TestDealClassificationAdapter(PromptLoaderService promptLoaderService) {
            super(ChatClient.builder(new org.springframework.ai.chat.model.ChatModel() {
                @Override
                public org.springframework.ai.chat.model.ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
                    return null;
                }
            }), promptLoaderService);
        }
    }
}

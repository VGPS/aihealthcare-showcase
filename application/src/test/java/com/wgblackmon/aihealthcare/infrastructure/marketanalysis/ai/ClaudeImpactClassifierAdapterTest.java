package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.ai;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.FactClassification;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDimension;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactDirection;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketImpactRank;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketNewsItem;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import com.wgblackmon.aihealthcare.infrastructure.config.PromptLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ClaudeImpactClassifierAdapter}.
 *
 * <p>Uses a no-op {@link ChatModel} stub to avoid Spring AI context bootstrap,
 * and tests {@code buildPrompt} / {@code parseResponse} directly.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClaudeImpactClassifierAdapterTest {

    @Mock
    private PromptLoaderService promptLoader;

    private ClaudeImpactClassifierAdapter adapter;

    private static final String PROMPT_TEMPLATE = "Classify these entries:\n{entries}";

    @BeforeEach
    void setUp() {
        when(promptLoader.load("market-impact-classify.txt")).thenReturn(PROMPT_TEMPLATE);
        adapter = new TestClaudeImpactClassifierAdapter(promptLoader);
    }

    // ─── buildPrompt ─────────────────────────────────────────────────────────

    @Test
    void buildPrompt_includesEntryHeadlineAndCategory() {
        List<MarketDigestEntry> entries = List.of(
                entry("Epic acquires startup", NewsCategory.M_AND_A, null),
                entry("Earnings beat Q2", NewsCategory.EARNINGS, null)
        );

        String prompt = adapter.buildPrompt(entries);

        assertThat(prompt).contains("[1]");
        assertThat(prompt).contains("Epic acquires startup");
        assertThat(prompt).contains("M_AND_A");
        assertThat(prompt).contains("[2]");
        assertThat(prompt).contains("Earnings beat Q2");
        assertThat(prompt).contains("EARNINGS");
        assertThat(prompt).contains("Classify these entries:");
    }

    @Test
    void buildPrompt_includesDealSizeWhenPresent() {
        List<MarketDigestEntry> entries = List.of(
                entry("Big deal", NewsCategory.FUNDING, 200_000_000L)
        );

        String prompt = adapter.buildPrompt(entries);

        assertThat(prompt).contains("200000000");
    }

    // ─── parseResponse — happy path ──────────────────────────────────────────

    @Test
    void parseResponse_withValidResponse_enrichesEntry() {
        String response = """
                ENTRY: 1
                FACT: CONFIRMED
                RANK: 2
                REVENUE: POSITIVE|Acquisition adds new revenue stream.
                EARNINGS: NEUTRAL|Deal costs offset near-term earnings.
                VALUATION: POSITIVE|AI capability premium justified.
                INVESTOR_SENTIMENT: POSITIVE|Market views AI acquisitions favorably.
                FUTURE_GROWTH: POSITIVE|Positions company for AI-driven growth.
                """;

        List<MarketDigestEntry> originals = List.of(
                entry("Epic acquires startup", NewsCategory.M_AND_A, null)
        );

        List<MarketDigestEntry> result = adapter.parseResponse(response, originals);

        assertThat(result).hasSize(1);
        MarketDigestEntry enriched = result.get(0);
        assertThat(enriched.factClassification()).isEqualTo(FactClassification.CONFIRMED);
        assertThat(enriched.rank().value()).isEqualTo(2);
        assertThat(enriched.impactAssessments()).hasSize(5);
    }

    @Test
    void parseResponse_parsesAllFiveDimensions() {
        String response = """
                ENTRY: 1
                FACT: CONFIRMED
                RANK: 1
                REVENUE: POSITIVE|Strong revenue impact.
                EARNINGS: NEGATIVE|Short-term earnings drag.
                VALUATION: POSITIVE|Valuation upside.
                INVESTOR_SENTIMENT: NEUTRAL|Mixed market reaction.
                FUTURE_GROWTH: POSITIVE|Long-term growth driver.
                """;

        List<MarketDigestEntry> originals = List.of(
                entry("Headline", NewsCategory.EARNINGS, null)
        );
        List<MarketDigestEntry> result = adapter.parseResponse(response, originals);

        assertThat(result.get(0).impactAssessments())
                .extracting(a -> a.dimension())
                .containsExactlyInAnyOrder(
                        ImpactDimension.REVENUE,
                        ImpactDimension.EARNINGS,
                        ImpactDimension.VALUATION,
                        ImpactDimension.INVESTOR_SENTIMENT,
                        ImpactDimension.FUTURE_GROWTH
                );
        assertThat(result.get(0).impactAssessments())
                .filteredOn(a -> a.dimension() == ImpactDimension.EARNINGS)
                .extracting(a -> a.direction())
                .containsExactly(ImpactDirection.NEGATIVE);
    }

    // ─── parseResponse — graceful degradation ────────────────────────────────

    @Test
    void parseResponse_emptyResponse_appliesDefaults() {
        List<MarketDigestEntry> originals = List.of(
                entry("Some headline", NewsCategory.REGULATORY, null)
        );

        List<MarketDigestEntry> result = adapter.parseResponse("", originals);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).factClassification()).isEqualTo(FactClassification.SPECULATIVE);
        assertThat(result.get(0).rank().value()).isEqualTo(5);
        assertThat(result.get(0).impactAssessments()).isEmpty();
    }

    @Test
    void parseResponse_unknownFactValue_defaultsToSpeculative() {
        String response = """
                ENTRY: 1
                FACT: MAYBE
                RANK: 3
                """;

        List<MarketDigestEntry> originals = List.of(entry("Headline", NewsCategory.OTHER, null));
        List<MarketDigestEntry> result = adapter.parseResponse(response, originals);

        assertThat(result.get(0).factClassification()).isEqualTo(FactClassification.SPECULATIVE);
    }

    @Test
    void classify_emptyInput_returnsEmptyList() {
        List<MarketDigestEntry> result = adapter.classify(List.of());
        assertThat(result).isEmpty();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private MarketDigestEntry entry(String headline, NewsCategory category, Long dealSize) {
        MarketNewsItem item = new MarketNewsItem(
                headline,
                "Summary for " + headline,
                List.of(),
                Instant.now(),
                category,
                dealSize
        );
        return new MarketDigestEntry(
                item,
                List.of(),
                FactClassification.SPECULATIVE,
                new MarketImpactRank(5),
                List.of()
        );
    }

    /** Avoids real ChatClient bootstrap — no-op ChatModel returns null. */
    private static class TestClaudeImpactClassifierAdapter extends ClaudeImpactClassifierAdapter {
        TestClaudeImpactClassifierAdapter(PromptLoaderService promptLoader) {
            super(ChatClient.builder(new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) { return null; }
            }), promptLoader);
        }
    }
}

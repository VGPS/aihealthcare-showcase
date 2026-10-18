package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.LlmCallUsage;
import com.wgblackmon.aihealthcare.domain.model.LlmUsageSummary;
import com.wgblackmon.aihealthcare.domain.model.ModelPricing;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link LlmUsageSummaryService}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
class LlmUsageSummaryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    private LlmUsageSummaryService priced() {
        return new LlmUsageSummaryService(Map.of(
                "claude-sonnet-4-6", new ModelPricing(3.00, 15.00),
                "claude-haiku-4-5", new ModelPricing(1.00, 5.00)
        ));
    }

    private LlmCallUsage usage(String pipelineId, String model, int promptTokens, int outputTokens) {
        return new LlmCallUsage(null, pipelineId, model, promptTokens, outputTokens,
                promptTokens + outputTokens, NOW);
    }

    @Test
    void summarizeByModel_aggregatesTokensAndCostPerModel() {
        List<LlmCallUsage> usages = List.of(
                usage("legal-trends", "claude-sonnet-4-6", 1_000_000, 100_000),
                usage("deal-signals", "claude-sonnet-4-6", 1_000_000, 100_000),
                usage("framework-analysis", "claude-haiku-4-5", 1_000_000, 100_000)
        );

        List<LlmUsageSummary> result = priced().summarizeByModel(usages);

        assertThat(result).hasSize(2);
        LlmUsageSummary sonnet = result.stream().filter(s -> s.key().equals("claude-sonnet-4-6")).findFirst().orElseThrow();
        assertThat(sonnet.callCount()).isEqualTo(2);
        assertThat(sonnet.promptTokens()).isEqualTo(2_000_000);
        assertThat(sonnet.outputTokens()).isEqualTo(200_000);
        // 2 calls * (1M * $3/M + 0.1M * $15/M) = 2 * (3.00 + 1.50) = 9.00
        assertThat(sonnet.estimatedCostUsd()).isCloseTo(9.00, within(0.001));
    }

    @Test
    void summarizeByModel_sortsMostExpensiveFirst() {
        List<LlmCallUsage> usages = List.of(
                usage("a", "claude-haiku-4-5", 100_000, 10_000),
                usage("b", "claude-sonnet-4-6", 1_000_000, 100_000)
        );

        List<LlmUsageSummary> result = priced().summarizeByModel(usages);

        assertThat(result.get(0).key()).isEqualTo("claude-sonnet-4-6");
        assertThat(result.get(1).key()).isEqualTo("claude-haiku-4-5");
    }

    @Test
    void summarizeByPipeline_aggregatesPerPipelineRegardlessOfModel() {
        List<LlmCallUsage> usages = List.of(
                usage("legal-trends", "claude-sonnet-4-6", 1_000, 100),
                usage("legal-trends", "claude-sonnet-4-6", 1_000, 100),
                usage("deal-signals", "claude-sonnet-4-6", 1_000, 100)
        );

        List<LlmUsageSummary> result = priced().summarizeByPipeline(usages);

        LlmUsageSummary legalTrends = result.stream().filter(s -> s.key().equals("legal-trends")).findFirst().orElseThrow();
        assertThat(legalTrends.callCount()).isEqualTo(2);
        assertThat(legalTrends.totalTokens()).isEqualTo(2_200);
    }

    @Test
    void summarizeByDay_groupsByUtcCalendarDayMostRecentFirst() {
        List<LlmCallUsage> usages = List.of(
                new LlmCallUsage(null, "legal-trends", "claude-sonnet-4-6", 100, 10, 110,
                        Instant.parse("2026-10-17T23:59:00Z")),
                new LlmCallUsage(null, "legal-trends", "claude-sonnet-4-6", 100, 10, 110,
                        Instant.parse("2026-10-18T00:01:00Z"))
        );

        List<LlmUsageSummary> result = priced().summarizeByDay(usages);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).key()).isEqualTo("2026-10-18");
        assertThat(result.get(1).key()).isEqualTo("2026-10-17");
    }

    @Test
    void summarize_unpricedModelReportsZeroCost() {
        List<LlmCallUsage> usages = List.of(usage("company-discovery", "perplexity/sonar", 1_000_000, 100_000));

        List<LlmUsageSummary> result = priced().summarizeByModel(usages);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).estimatedCostUsd()).isZero();
        assertThat(result.get(0).totalTokens()).isEqualTo(1_100_000);
    }

    @Test
    void summarize_emptyInputReturnsEmptyList() {
        assertThat(priced().summarizeByModel(List.of())).isEmpty();
        assertThat(priced().summarizeByPipeline(List.of())).isEmpty();
        assertThat(priced().summarizeByDay(List.of())).isEmpty();
    }

    @Test
    void constructor_nullPricingMapTreatedAsEmpty() {
        LlmUsageSummaryService service = new LlmUsageSummaryService(null);
        List<LlmUsageSummary> result = service.summarizeByModel(List.of(usage("x", "claude-sonnet-4-6", 1000, 100)));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).estimatedCostUsd()).isZero();
    }
}

package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.model.LlmCallUsage;
import com.wgblackmon.aihealthcare.domain.model.LlmUsageSummary;
import com.wgblackmon.aihealthcare.domain.model.ModelPricing;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Pure domain service that aggregates {@link LlmCallUsage} rows into cost and
 * token summaries, grouped by model, by pipeline, or by calendar day.
 *
 * <p>Cost is estimated per row from a {@link ModelPricing} lookup supplied at
 * construction (sourced from {@code aihealthcare.llm-pricing} configuration).
 * A model with no configured entry contributes its token counts with zero
 * estimated cost rather than a guessed rate.
 *
 * <p>Carries no Spring annotations — wired as a {@code @Bean} in
 * {@code AppConfig}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public class LlmUsageSummaryService {

    private final Map<String, ModelPricing> pricingByModel;

    public LlmUsageSummaryService(Map<String, ModelPricing> pricingByModel) {
        this.pricingByModel = pricingByModel != null ? pricingByModel : Map.of();
    }

    /**
     * Aggregates usage rows by model ID, sorted by estimated cost descending.
     *
     * @param usages the usage rows to aggregate
     * @return one summary per distinct model, most expensive first
     */
    public List<LlmUsageSummary> summarizeByModel(List<LlmCallUsage> usages) {
        List<LlmUsageSummary> result = summarize(usages, LlmCallUsage::model);
        result.sort(Comparator.comparingDouble(LlmUsageSummary::estimatedCostUsd).reversed());
        return result;
    }

    /**
     * Aggregates usage rows by pipeline ID, sorted by estimated cost descending.
     *
     * <p>This is the actionable breakdown — it shows which scheduled pipeline is
     * driving spend, so a specific lever (frequency, model tier, context size)
     * can be adjusted, rather than just observing a total.
     *
     * @param usages the usage rows to aggregate
     * @return one summary per distinct pipeline, most expensive first
     */
    public List<LlmUsageSummary> summarizeByPipeline(List<LlmCallUsage> usages) {
        List<LlmUsageSummary> result = summarize(usages, LlmCallUsage::pipelineId);
        result.sort(Comparator.comparingDouble(LlmUsageSummary::estimatedCostUsd).reversed());
        return result;
    }

    /**
     * Aggregates usage rows by UTC calendar day, sorted most recent first.
     *
     * @param usages the usage rows to aggregate
     * @return one summary per distinct day (ISO date string key)
     */
    public List<LlmUsageSummary> summarizeByDay(List<LlmCallUsage> usages) {
        List<LlmUsageSummary> result = summarize(usages,
                usage -> usage.occurredAt().atZone(ZoneOffset.UTC).toLocalDate().toString());
        result.sort(Comparator.comparing(LlmUsageSummary::key).reversed());
        return result;
    }

    private List<LlmUsageSummary> summarize(List<LlmCallUsage> usages, Function<LlmCallUsage, String> keyFn) {
        Map<String, Accumulator> byKey = new LinkedHashMap<>();
        for (LlmCallUsage usage : usages) {
            Accumulator acc = byKey.computeIfAbsent(keyFn.apply(usage), k -> new Accumulator());
            acc.callCount++;
            acc.promptTokens += usage.promptTokens();
            acc.outputTokens += usage.outputTokens();
            acc.totalTokens += usage.totalTokens();
            acc.estimatedCostUsd += estimateCost(usage);
        }

        List<LlmUsageSummary> result = new ArrayList<>();
        for (Map.Entry<String, Accumulator> entry : byKey.entrySet()) {
            Accumulator acc = entry.getValue();
            result.add(new LlmUsageSummary(entry.getKey(), acc.callCount,
                    acc.promptTokens, acc.outputTokens, acc.totalTokens, acc.estimatedCostUsd));
        }
        return result;
    }

    private double estimateCost(LlmCallUsage usage) {
        ModelPricing pricing = pricingByModel.get(usage.model());
        if (pricing == null) {
            return 0.0;
        }
        return (usage.promptTokens() / 1_000_000.0) * pricing.inputPerMillionUsd()
                + (usage.outputTokens() / 1_000_000.0) * pricing.outputPerMillionUsd();
    }

    private static final class Accumulator {
        long callCount;
        long promptTokens;
        long outputTokens;
        long totalTokens;
        double estimatedCostUsd;
    }
}

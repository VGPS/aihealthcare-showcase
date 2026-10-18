package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record holding the per-million-token price of a single LLM model.
 *
 * <p>Supplied from externalized configuration ({@code aihealthcare.llm-pricing}) rather
 * than hardcoded, since published rates change and this project calls multiple models
 * across multiple providers. A model with no configured entry is treated as unpriced —
 * {@link com.wgblackmon.aihealthcare.domain.service.LlmUsageSummaryService} reports its
 * token counts with a zero estimated cost rather than guessing a rate.
 *
 * @param inputPerMillionUsd  price in USD per 1,000,000 input tokens
 * @param outputPerMillionUsd price in USD per 1,000,000 output tokens
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public record ModelPricing(double inputPerMillionUsd, double outputPerMillionUsd) {
}

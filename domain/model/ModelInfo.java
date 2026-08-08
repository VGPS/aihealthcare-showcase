package com.wgblackmon.aihealthcare.domain.model;

/**
 * Immutable domain record representing an available LLM provider and its
 * configured model identifier.
 *
 * <p>Used by the UI to dynamically render model selection checkboxes and
 * display which specific model version each provider is running.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-10
 * @updated 2026-07-10
 */
public record ModelInfo(String providerName, String modelId) {}

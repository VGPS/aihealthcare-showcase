package com.wgblackmon.aihealthcare.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Binds the {@code aihealthcare.llm-pricing} YAML section to a per-model
 * price table used to estimate LLM call cost.
 *
 * <p>Rates are externalized rather than hardcoded because published prices
 * change over time and this project calls multiple models across multiple
 * providers. A model with no entry here is reported with a zero estimated
 * cost by {@link com.wgblackmon.aihealthcare.domain.service.LlmUsageSummaryService}
 * rather than a guessed rate.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
@Component
@ConfigurationProperties(prefix = "aihealthcare.llm-pricing")
public class LlmPricingProperties {

    private Map<String, ModelEntry> models = new HashMap<>();

    public Map<String, ModelEntry> getModels() {
        return models;
    }

    public void setModels(Map<String, ModelEntry> models) {
        this.models = models;
    }

    /**
     * Mutable POJO for YAML binding — converted to immutable
     * {@link com.wgblackmon.aihealthcare.domain.model.ModelPricing} at
     * wiring time in {@code AppConfig}.
     */
    public static class ModelEntry {
        private double inputPerMillion;
        private double outputPerMillion;

        public double getInputPerMillion() { return inputPerMillion; }
        public void setInputPerMillion(double inputPerMillion) { this.inputPerMillion = inputPerMillion; }

        public double getOutputPerMillion() { return outputPerMillion; }
        public void setOutputPerMillion(double outputPerMillion) { this.outputPerMillion = outputPerMillion; }
    }
}

package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * An admin-curated, parameterised prompt in the enterprise data catalogue.
 *
 * <p>Canned prompts provide safe, structured data access — the template
 * is filled with validated parameters and mapped directly to a
 * {@link DataQueryPlan} without any LLM call. They are stored in
 * {@code enterprise_data_prompts} and surfaced on the console's Run tab.
 *
 * @param promptId     stable identifier (e.g. "legislation-by-state")
 * @param label        human-readable name shown in the dropdown
 * @param description  one-line explanation
 * @param feedId       the feed this prompt targets
 * @param templateText the prompt template with {@code {param}} placeholders
 * @param parameters   parameter definitions (drives the dynamic form)
 * @param minTier      minimum subscription tier required to use this prompt
 * @param active       whether this prompt is available for selection
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record CannedPrompt(
        String promptId,
        String label,
        String description,
        String feedId,
        String templateText,
        List<DataParameter> parameters,
        SubscriptionTier minTier,
        boolean active
) {
    public CannedPrompt {
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
    }
}

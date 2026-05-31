package com.wgblackmon.aihealthcare.infrastructure.ingestion.huggingface;

import java.util.List;
import java.util.Map;

/**
 * Infrastructure-only record for deserializing a single model entry from the
 * HuggingFace Models API response.
 *
 * <p>The HuggingFace API returns a JSON array of model objects.  This record
 * captures the subset of fields needed to construct a
 * {@link com.wgblackmon.aihealthcare.domain.model.NewsArticle} for the
 * newsletter pipeline.
 *
 * <p>Field names use underscores to match the HuggingFace JSON convention
 * (e.g. {@code pipeline_tag}, {@code model_id}).
 *
 * <p>{@code cardData} maps the model-card YAML frontmatter (language, license,
 * datasets, etc.).  {@code likes} is the community endorsement count.
 * {@code library_name} identifies the ML framework (transformers, diffusers, etc.).
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-04-19
 * @updated 2026-05-31
 */
public record HuggingFaceModelResponse(
        String modelId,
        String author,
        int downloads,
        String pipeline_tag,
        List<String> tags,
        String lastModified,
        Map<String, Object> cardData,
        int likes,
        String library_name
) {
}

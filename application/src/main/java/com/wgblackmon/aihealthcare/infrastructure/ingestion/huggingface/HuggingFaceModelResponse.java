package com.wgblackmon.aihealthcare.infrastructure.ingestion.huggingface;

import java.util.List;

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
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
public record HuggingFaceModelResponse(
        String modelId,
        String author,
        int downloads,
        String pipeline_tag,
        List<String> tags,
        String lastModified
) {
}

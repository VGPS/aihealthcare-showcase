package com.wgblackmon.aihealthcare.domain.port.inbound;

import com.wgblackmon.aihealthcare.domain.model.ComparisonResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationResult;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.PromptVariant;

import java.util.List;

/**
 * Inbound port — drive prompt evaluation and comparison workflows.
 *
 * <p>The implementation lives in {@code domain/service} and orchestrates calls
 * to {@link com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort},
 * {@link com.wgblackmon.aihealthcare.domain.port.outbound.AiEvaluationPort},
 * and persistence ports.  The {@code web} layer calls this interface; it never
 * touches the service class directly.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
public interface EvaluatePromptsUseCase {

    /**
     * Create and persist a new prompt variant.
     *
     * @param variantId    unique identifier for this variant
     * @param name         human-readable label
     * @param templateText the full prompt template with placeholders
     * @param description  free-text description of the experiment
     * @return the created variant
     */
    PromptVariant createVariant(String variantId, String name,
                                String templateText, String description);

    /**
     * Retrieve all stored prompt variants.
     *
     * @return all variants, or an empty list
     */
    List<PromptVariant> listVariants();

    /**
     * Retrieve a single prompt variant by ID.
     *
     * @param variantId the variant identifier
     * @return the matching variant
     * @throws com.wgblackmon.aihealthcare.domain.exception.PromptVariantNotFoundException
     *         if not found
     */
    PromptVariant getVariant(String variantId);

    /**
     * Delete a prompt variant by ID.
     *
     * @param variantId the variant identifier
     * @throws com.wgblackmon.aihealthcare.domain.exception.PromptVariantNotFoundException
     *         if not found
     */
    void deleteVariant(String variantId);

    /**
     * Run a single evaluation: summarize the given articles using the specified
     * prompt variant, then score the output with the AI evaluator.
     *
     * @param variantId  the prompt variant to evaluate
     * @param articleIds IDs of articles to use as input
     * @param topic      the topic for summarization
     * @param tone       the tone for summarization
     * @return the full evaluation result including section output and scores
     */
    EvaluationResult evaluate(String variantId, List<String> articleIds,
                              String topic, NewsletterTone tone);

    /**
     * Run a side-by-side comparison: evaluate the same articles against multiple
     * prompt variants and group the results.
     *
     * @param variantIds IDs of the variants to compare (minimum 2)
     * @param articleIds IDs of articles to use as shared input
     * @param topic      the topic for summarization
     * @param tone       the tone for summarization
     * @return a comparison result grouping all individual evaluations
     */
    ComparisonResult compare(List<String> variantIds, List<String> articleIds,
                             String topic, NewsletterTone tone);

    /**
     * Retrieve a single evaluation result by ID.
     *
     * @param evaluationId the evaluation identifier
     * @return the matching result
     * @throws com.wgblackmon.aihealthcare.domain.exception.EvaluationNotFoundException
     *         if not found
     */
    EvaluationResult getEvaluation(String evaluationId);

    /**
     * Retrieve all stored evaluation results.
     *
     * @return all results, or an empty list
     */
    List<EvaluationResult> listEvaluations();

    /**
     * Retrieve all evaluation results for a specific variant.
     *
     * @param variantId the variant identifier
     * @return matching results, or an empty list
     */
    List<EvaluationResult> listEvaluationsByVariant(String variantId);

    /**
     * Retrieve a single comparison result by ID.
     *
     * @param comparisonId the comparison identifier
     * @return the matching comparison
     * @throws com.wgblackmon.aihealthcare.domain.exception.EvaluationNotFoundException
     *         if not found
     */
    ComparisonResult getComparison(String comparisonId);

    /**
     * Retrieve all stored comparison results.
     *
     * @return all comparisons, or an empty list
     */
    List<ComparisonResult> listComparisons();
}

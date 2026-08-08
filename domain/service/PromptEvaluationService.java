package com.wgblackmon.aihealthcare.domain.service;

import com.wgblackmon.aihealthcare.domain.exception.EvaluationNotFoundException;
import com.wgblackmon.aihealthcare.domain.exception.PromptVariantNotFoundException;
import com.wgblackmon.aihealthcare.domain.model.ComparisonResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationScore;
import com.wgblackmon.aihealthcare.domain.model.NewsArticle;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.model.PromptVariant;
import com.wgblackmon.aihealthcare.domain.port.inbound.EvaluatePromptsUseCase;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiEvaluationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.AiSummarizationPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.ArticleIngestionPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.EvaluationResultPort;
import com.wgblackmon.aihealthcare.domain.port.outbound.PromptVariantPort;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Application service implementing the prompt evaluation and comparison workflow.
 *
 * <p>Orchestrates the creation of prompt variants, evaluation of variants against
 * sample articles (summarize + AI-judge scoring), and side-by-side comparisons of
 * multiple variants.  All results are persisted for historical review.
 *
 * <p>This class carries no Spring annotations; {@code AppConfig} wires it as a
 * {@code @Bean} so the application layer remains framework-free and trivially
 * testable without a Spring context.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-17
 * @updated 2026-04-17
 */
@Slf4j
public class PromptEvaluationService implements EvaluatePromptsUseCase {

    private final PromptVariantPort    variantPort;
    private final AiSummarizationPort  summarizationPort;
    private final AiEvaluationPort     evaluationPort;
    private final ArticleIngestionPort ingestionPort;
    private final EvaluationResultPort resultPort;

    public PromptEvaluationService(PromptVariantPort variantPort,
                                   AiSummarizationPort summarizationPort,
                                   AiEvaluationPort evaluationPort,
                                   ArticleIngestionPort ingestionPort,
                                   EvaluationResultPort resultPort) {
        log.debug("PromptEvaluationService() | variantPort={}, summarizationPort={}, "
                  + "evaluationPort={}, ingestionPort={}, resultPort={}",
                  variantPort, summarizationPort, evaluationPort, ingestionPort, resultPort);
        this.variantPort       = variantPort;
        this.summarizationPort = summarizationPort;
        this.evaluationPort    = evaluationPort;
        this.ingestionPort     = ingestionPort;
        this.resultPort        = resultPort;
    }

    // -------------------------------------------------------------------------
    // Variant CRUD
    // -------------------------------------------------------------------------

    @Override
    public PromptVariant createVariant(String variantId, String name,
                                       String templateText, String description) {
        log.debug("createVariant() | variantId={}, name={}", variantId, name);

        PromptVariant variant = new PromptVariant(
                variantId, name, templateText, description, Instant.now());
        variantPort.save(variant);

        log.info("createVariant() | Variant created: variantId={}", variantId);
        log.debug("createVariant() | return={}", variant);
        return variant;
    }

    @Override
    public List<PromptVariant> listVariants() {
        log.debug("listVariants() | (no args)");

        List<PromptVariant> result = variantPort.findAll();

        log.debug("listVariants() | return={} variants", result.size());
        return result;
    }

    @Override
    public PromptVariant getVariant(String variantId) {
        log.debug("getVariant() | variantId={}", variantId);

        PromptVariant result = variantPort.findByVariantId(variantId);

        log.debug("getVariant() | return={}", result);
        return result;
    }

    @Override
    public void deleteVariant(String variantId) {
        log.debug("deleteVariant() | variantId={}", variantId);

        variantPort.delete(variantId);

        log.info("deleteVariant() | Variant deleted: variantId={}", variantId);
        log.debug("deleteVariant() | return=void");
    }

    // -------------------------------------------------------------------------
    // Evaluation
    // -------------------------------------------------------------------------

    @Override
    public EvaluationResult evaluate(String variantId, List<String> articleIds,
                                     String topic, NewsletterTone tone) {
        log.debug("evaluate() | variantId={}, articleIds={}, topic={}, tone={}",
                  variantId, articleIds, topic, tone);

        // 1. Load the prompt variant
        PromptVariant variant = variantPort.findByVariantId(variantId);

        // 2. Load the articles
        List<NewsArticle> articles = ingestionPort.fetchArticlesByIds(articleIds);
        if (articles.isEmpty()) {
            throw new IllegalArgumentException(
                    "No articles found for the given IDs: " + articleIds);
        }

        // 3. Summarize using the variant's template
        String sectionId = "eval-sec-" + UUID.randomUUID().toString().substring(0, 8);
        NewsletterSection section = summarizationPort.summarizeWithTemplate(
                articles, topic, tone, sectionId, variant.templateText());

        // 4. Score the output with the AI evaluator
        EvaluationScore score = evaluationPort.evaluate(section, articles, topic, tone);

        // 5. Build and persist the result
        String evaluationId = UUID.randomUUID().toString();
        EvaluationResult result = new EvaluationResult(
                evaluationId, variantId, variant.name(), articleIds,
                topic, tone, section, score, Instant.now());
        resultPort.save(result);

        log.info("evaluate() | Evaluation complete: evaluationId={}, variantId={}, overall={}",
                 evaluationId, variantId, score.overall());
        log.debug("evaluate() | return={}", result);
        return result;
    }

    // -------------------------------------------------------------------------
    // Comparison
    // -------------------------------------------------------------------------

    @Override
    public ComparisonResult compare(List<String> variantIds, List<String> articleIds,
                                    String topic, NewsletterTone tone) {
        log.debug("compare() | variantIds={}, articleIds={}, topic={}, tone={}",
                  variantIds, articleIds, topic, tone);

        if (variantIds == null || variantIds.size() < 2) {
            throw new IllegalArgumentException("compare requires at least two variant IDs");
        }

        List<EvaluationResult> results = new ArrayList<>();
        for (String variantId : variantIds) {
            EvaluationResult evalResult = evaluate(variantId, articleIds, topic, tone);
            results.add(evalResult);
        }

        String comparisonId = UUID.randomUUID().toString();
        ComparisonResult result = new ComparisonResult(
                comparisonId, articleIds, topic, tone, results, Instant.now());
        resultPort.saveComparison(result);

        log.info("compare() | Comparison complete: comparisonId={}, variantCount={}",
                 comparisonId, variantIds.size());
        log.debug("compare() | return={}", result);
        return result;
    }

    // -------------------------------------------------------------------------
    // Result retrieval
    // -------------------------------------------------------------------------

    @Override
    public EvaluationResult getEvaluation(String evaluationId) {
        log.debug("getEvaluation() | evaluationId={}", evaluationId);

        EvaluationResult result = resultPort.findByEvaluationId(evaluationId);

        log.debug("getEvaluation() | return={}", result);
        return result;
    }

    @Override
    public List<EvaluationResult> listEvaluations() {
        log.debug("listEvaluations() | (no args)");

        List<EvaluationResult> result = resultPort.findAll();

        log.debug("listEvaluations() | return={} evaluations", result.size());
        return result;
    }

    @Override
    public List<EvaluationResult> listEvaluationsByVariant(String variantId) {
        log.debug("listEvaluationsByVariant() | variantId={}", variantId);

        List<EvaluationResult> result = resultPort.findByVariantId(variantId);

        log.debug("listEvaluationsByVariant() | return={} evaluations", result.size());
        return result;
    }

    @Override
    public ComparisonResult getComparison(String comparisonId) {
        log.debug("getComparison() | comparisonId={}", comparisonId);

        ComparisonResult result = resultPort.findComparisonById(comparisonId);

        log.debug("getComparison() | return={}", result);
        return result;
    }

    @Override
    public List<ComparisonResult> listComparisons() {
        log.debug("listComparisons() | (no args)");

        List<ComparisonResult> result = resultPort.findAllComparisons();

        log.debug("listComparisons() | return={} comparisons", result.size());
        return result;
    }
}

package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.ComparisonResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationResult;
import com.wgblackmon.aihealthcare.domain.model.EvaluationScore;
import com.wgblackmon.aihealthcare.domain.model.NewsletterSection;
import com.wgblackmon.aihealthcare.domain.model.NewsletterTone;
import com.wgblackmon.aihealthcare.domain.port.inbound.EvaluatePromptsUseCase;
import com.wgblackmon.aihealthcare.web.dto.CompareRequest;
import com.wgblackmon.aihealthcare.web.dto.ComparisonResultResponse;
import com.wgblackmon.aihealthcare.web.dto.EvalSectionResponse;
import com.wgblackmon.aihealthcare.web.dto.EvaluateRequest;
import com.wgblackmon.aihealthcare.web.dto.EvaluationResultResponse;
import com.wgblackmon.aihealthcare.web.dto.EvaluationScoreResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for prompt evaluation and comparison workflows.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /api/v1/evaluations}                    — run a single evaluation</li>
 *   <li>{@code GET  /api/v1/evaluations}                    — list all evaluations</li>
 *   <li>{@code GET  /api/v1/evaluations/{id}}               — get evaluation by ID</li>
 *   <li>{@code GET  /api/v1/evaluations?variantId={id}}     — list evaluations by variant</li>
 *   <li>{@code POST /api/v1/comparisons}                    — run a side-by-side comparison</li>
 *   <li>{@code GET  /api/v1/comparisons}                    — list all comparisons</li>
 *   <li>{@code GET  /api/v1/comparisons/{id}}               — get comparison by ID</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-18
 * @updated 2026-04-18
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class PromptEvaluationController {

    private final EvaluatePromptsUseCase evaluatePromptsUseCase;

    public PromptEvaluationController(EvaluatePromptsUseCase evaluatePromptsUseCase) {
        log.debug("PromptEvaluationController() | evaluatePromptsUseCase={}",
                  evaluatePromptsUseCase.getClass().getSimpleName());
        this.evaluatePromptsUseCase = evaluatePromptsUseCase;
    }

    // -------------------------------------------------------------------------
    // Evaluation endpoints
    // -------------------------------------------------------------------------

    @PostMapping("/evaluations")
    public ResponseEntity<EvaluationResultResponse> evaluate(@RequestBody EvaluateRequest request) {
        log.debug("evaluate() | request={}", request);

        NewsletterTone tone = NewsletterTone.valueOf(request.tone());
        EvaluationResult evalResult = evaluatePromptsUseCase.evaluate(
                request.variantId(), request.articleIds(), request.topic(), tone);

        EvaluationResultResponse result = toEvalResponse(evalResult);
        log.info("evaluate() | Evaluation complete: evaluationId={}", evalResult.evaluationId());
        log.debug("evaluate() | return={}", result);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/evaluations")
    public ResponseEntity<List<EvaluationResultResponse>> listEvaluations(
            @RequestParam(required = false) String variantId) {
        log.debug("listEvaluations() | variantId={}", variantId);

        List<EvaluationResult> evaluations;
        if (variantId != null && !variantId.isBlank()) {
            evaluations = evaluatePromptsUseCase.listEvaluationsByVariant(variantId);
        } else {
            evaluations = evaluatePromptsUseCase.listEvaluations();
        }

        List<EvaluationResultResponse> result = new ArrayList<>();
        for (EvaluationResult eval : evaluations) {
            result.add(toEvalResponse(eval));
        }

        log.debug("listEvaluations() | return={} evaluations", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/evaluations/{evaluationId}")
    public ResponseEntity<EvaluationResultResponse> getEvaluation(@PathVariable String evaluationId) {
        log.debug("getEvaluation() | evaluationId={}", evaluationId);

        EvaluationResult evalResult = evaluatePromptsUseCase.getEvaluation(evaluationId);

        EvaluationResultResponse result = toEvalResponse(evalResult);
        log.debug("getEvaluation() | return={}", result);
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // Comparison endpoints
    // -------------------------------------------------------------------------

    @PostMapping("/comparisons")
    public ResponseEntity<ComparisonResultResponse> compare(@RequestBody CompareRequest request) {
        log.debug("compare() | request={}", request);

        NewsletterTone tone = NewsletterTone.valueOf(request.tone());
        ComparisonResult comparison = evaluatePromptsUseCase.compare(
                request.variantIds(), request.articleIds(), request.topic(), tone);

        ComparisonResultResponse result = toComparisonResponse(comparison);
        log.info("compare() | Comparison complete: comparisonId={}", comparison.comparisonId());
        log.debug("compare() | return={}", result);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/comparisons")
    public ResponseEntity<List<ComparisonResultResponse>> listComparisons() {
        log.debug("listComparisons() | (no args)");

        List<ComparisonResult> comparisons = evaluatePromptsUseCase.listComparisons();
        List<ComparisonResultResponse> result = new ArrayList<>();
        for (ComparisonResult comp : comparisons) {
            result.add(toComparisonResponse(comp));
        }

        log.debug("listComparisons() | return={} comparisons", result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/comparisons/{comparisonId}")
    public ResponseEntity<ComparisonResultResponse> getComparison(@PathVariable String comparisonId) {
        log.debug("getComparison() | comparisonId={}", comparisonId);

        ComparisonResult comparison = evaluatePromptsUseCase.getComparison(comparisonId);

        ComparisonResultResponse result = toComparisonResponse(comparison);
        log.debug("getComparison() | return={}", result);
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // Domain → DTO mapping
    // -------------------------------------------------------------------------

    private EvaluationResultResponse toEvalResponse(EvaluationResult eval) {
        log.debug("toEvalResponse() | evaluationId={}", eval.evaluationId());

        NewsletterSection section = eval.section();
        EvalSectionResponse sectionResponse = new EvalSectionResponse(
                section.sectionId(),
                section.sectionType().name(),
                section.topic(),
                section.headline(),
                section.summary(),
                section.articleIds());

        EvaluationScore score = eval.score();
        EvaluationScoreResponse scoreResponse = new EvaluationScoreResponse(
                score.relevance(),
                score.conciseness(),
                score.attributionQuality(),
                score.toneMatch(),
                score.completeness(),
                score.overall(),
                score.scoringNotes());

        EvaluationResultResponse result = new EvaluationResultResponse(
                eval.evaluationId(),
                eval.variantId(),
                eval.variantName(),
                eval.articleIds(),
                eval.topic(),
                eval.tone().name(),
                sectionResponse,
                scoreResponse,
                eval.evaluatedAt());

        log.debug("toEvalResponse() | return={}", result);
        return result;
    }

    private ComparisonResultResponse toComparisonResponse(ComparisonResult comparison) {
        log.debug("toComparisonResponse() | comparisonId={}", comparison.comparisonId());

        List<EvaluationResultResponse> evalResponses = new ArrayList<>();
        for (EvaluationResult eval : comparison.results()) {
            evalResponses.add(toEvalResponse(eval));
        }

        ComparisonResultResponse result = new ComparisonResultResponse(
                comparison.comparisonId(),
                comparison.articleIds(),
                comparison.topic(),
                comparison.tone().name(),
                evalResponses,
                comparison.comparedAt());

        log.debug("toComparisonResponse() | return={}", result);
        return result;
    }
}

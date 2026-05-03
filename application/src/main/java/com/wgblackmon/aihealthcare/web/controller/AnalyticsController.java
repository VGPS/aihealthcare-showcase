package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CountByLabel;
import com.wgblackmon.aihealthcare.domain.model.EvaluationAnalytics;
import com.wgblackmon.aihealthcare.domain.model.IngestionAnalytics;
import com.wgblackmon.aihealthcare.domain.model.RunAnalytics;
import com.wgblackmon.aihealthcare.domain.model.VariantScore;
import com.wgblackmon.aihealthcare.domain.port.inbound.GetAnalyticsUseCase;
import com.wgblackmon.aihealthcare.web.dto.CountByLabelResponse;
import com.wgblackmon.aihealthcare.web.dto.EvaluationAnalyticsResponse;
import com.wgblackmon.aihealthcare.web.dto.IngestionAnalyticsResponse;
import com.wgblackmon.aihealthcare.web.dto.RunAnalyticsResponse;
import com.wgblackmon.aihealthcare.web.dto.VariantScoreResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller exposing the analytics dashboard endpoints.
 *
 * <p>Three read-only GET endpoints provide aggregate statistics covering
 * article ingestion, newsletter run production, and prompt evaluation
 * performance.  All responses are computed live from the database on
 * each request — there is no caching layer.
 *
 * <p>The controller maps domain records to web DTOs and never passes
 * domain objects directly to the HTTP response.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-03
 * @updated 2026-05-03
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final GetAnalyticsUseCase analyticsUseCase;

    public AnalyticsController(GetAnalyticsUseCase analyticsUseCase) {
        this.analyticsUseCase = analyticsUseCase;
    }

    /**
     * GET /api/v1/analytics/ingestion
     *
     * @return ingestion analytics response
     */
    @GetMapping("/ingestion")
    public ResponseEntity<IngestionAnalyticsResponse> getIngestionAnalytics() {
        log.debug("getIngestionAnalytics()");

        IngestionAnalytics analytics = analyticsUseCase.getIngestionAnalytics();

        List<CountByLabelResponse> byTier = new ArrayList<>();
        for (CountByLabel entry : analytics.bySourceTier()) {
            byTier.add(new CountByLabelResponse(entry.label(), entry.count()));
        }

        List<CountByLabelResponse> byTopic = new ArrayList<>();
        for (CountByLabel entry : analytics.byTopic()) {
            byTopic.add(new CountByLabelResponse(entry.label(), entry.count()));
        }

        IngestionAnalyticsResponse response = new IngestionAnalyticsResponse(
                analytics.totalArticles(),
                byTier,
                byTopic,
                analytics.last7DaysCount(),
                analytics.last30DaysCount());

        log.debug("getIngestionAnalytics() | return={}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/analytics/runs
     *
     * @return run analytics response
     */
    @GetMapping("/runs")
    public ResponseEntity<RunAnalyticsResponse> getRunAnalytics() {
        log.debug("getRunAnalytics()");

        RunAnalytics analytics = analyticsUseCase.getRunAnalytics();

        RunAnalyticsResponse response = new RunAnalyticsResponse(
                analytics.totalRuns(),
                analytics.draftCount(),
                analytics.sentCount(),
                analytics.archivedCount(),
                analytics.mostRecentRunAt());

        log.debug("getRunAnalytics() | return={}", response);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/analytics/evaluations
     *
     * @return evaluation analytics response
     */
    @GetMapping("/evaluations")
    public ResponseEntity<EvaluationAnalyticsResponse> getEvaluationAnalytics() {
        log.debug("getEvaluationAnalytics()");

        EvaluationAnalytics analytics = analyticsUseCase.getEvaluationAnalytics();

        List<VariantScoreResponse> variantScores = new ArrayList<>();
        for (VariantScore vs : analytics.variantScores()) {
            variantScores.add(new VariantScoreResponse(
                    vs.variantId(),
                    vs.variantName(),
                    vs.evaluationCount(),
                    vs.avgOverall(),
                    vs.avgRelevance(),
                    vs.avgConciseness(),
                    vs.avgCompleteness(),
                    vs.avgToneMatch(),
                    vs.avgAttributionQuality()));
        }

        EvaluationAnalyticsResponse response = new EvaluationAnalyticsResponse(
                analytics.totalEvaluations(),
                analytics.totalComparisons(),
                variantScores,
                analytics.bestVariantId());

        log.debug("getEvaluationAnalytics() | return={}", response);
        return ResponseEntity.ok(response);
    }
}

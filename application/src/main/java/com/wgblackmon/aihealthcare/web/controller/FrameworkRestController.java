package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.FrameworkAnalysis;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeFrameworksUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing healthcare framework competitive analysis endpoints.
 *
 * <p>{@code GET /api/v1/frameworks} returns all stored analyses.
 * {@code GET /api/v1/frameworks/{slug}} returns a single company's analysis.
 * {@code POST /api/v1/frameworks/analyze} triggers a full analysis run.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/frameworks")
public class FrameworkRestController {

    private final AnalyzeFrameworksUseCase frameworksUseCase;

    public FrameworkRestController(AnalyzeFrameworksUseCase frameworksUseCase) {
        log.debug("FrameworkRestController() | frameworksUseCase={}", frameworksUseCase);
        this.frameworksUseCase = frameworksUseCase;
    }

    /**
     * Returns all stored framework analyses, ordered by overall score descending.
     *
     * @return 200 with list of analyses (may be empty)
     */
    @GetMapping
    public ResponseEntity<List<FrameworkAnalysis>> getAll() {
        log.debug("getAll()");

        List<FrameworkAnalysis> result = frameworksUseCase.getAll();

        log.debug("getAll() | return={} analyses", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the most recent analysis for a single company.
     *
     * @param slug the company's slug identifier
     * @return 200 with the analysis, or 404 if not found
     */
    @GetMapping("/{slug}")
    public ResponseEntity<FrameworkAnalysis> getBySlug(@PathVariable String slug) {
        log.debug("getBySlug() | slug={}", slug);

        Optional<FrameworkAnalysis> opt = frameworksUseCase.getBySlug(slug);
        if (opt.isPresent()) {
            log.debug("getBySlug() | return=200");
            return ResponseEntity.ok(opt.get());
        }

        log.debug("getBySlug() | return=404");
        return ResponseEntity.notFound().build();
    }

    /**
     * Triggers a full competitive analysis run for all configured companies.
     *
     * @return 200 with the list of completed analyses
     */
    @PostMapping("/analyze")
    public ResponseEntity<List<FrameworkAnalysis>> analyzeAll() {
        log.debug("analyzeAll()");

        List<FrameworkAnalysis> result = frameworksUseCase.analyzeAll();

        log.debug("analyzeAll() | return={} analyses", result.size());
        return ResponseEntity.ok(result);
    }
}

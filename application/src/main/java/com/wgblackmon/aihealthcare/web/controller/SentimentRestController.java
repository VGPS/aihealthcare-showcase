package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.model.CompanySentiment;
import com.wgblackmon.aihealthcare.domain.port.inbound.AnalyzeCompanySentimentUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing company sentiment analysis endpoints.
 *
 * <p>Provides JSON endpoints for sentiment retrieval and manual analysis
 * trigger.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sentiment")
public class SentimentRestController {

    private final AnalyzeCompanySentimentUseCase sentimentUseCase;

    public SentimentRestController(AnalyzeCompanySentimentUseCase sentimentUseCase) {
        log.debug("SentimentRestController() | sentimentUseCase={}", sentimentUseCase);
        this.sentimentUseCase = sentimentUseCase;
    }

    /**
     * Returns all persisted company sentiments.
     */
    @GetMapping
    public ResponseEntity<List<CompanySentiment>> getAll() {
        log.debug("getAll()");
        List<CompanySentiment> result = sentimentUseCase.getAll();
        log.debug("getAll() | return={} sentiments", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * Returns the sentiment for a specific company.
     */
    @GetMapping("/{slug}")
    public ResponseEntity<CompanySentiment> getBySlug(@PathVariable String slug) {
        log.debug("getBySlug() | slug={}", slug);
        return sentimentUseCase.getBySlug(slug)
                .map(s -> {
                    log.debug("getBySlug() | return=present");
                    return ResponseEntity.ok(s);
                })
                .orElseGet(() -> {
                    log.debug("getBySlug() | return=notFound");
                    return ResponseEntity.notFound().build();
                });
    }

    /**
     * Triggers a full sentiment re-analysis for all tracked companies.
     */
    @PostMapping("/analyze")
    public ResponseEntity<List<CompanySentiment>> analyzeAll() {
        log.debug("analyzeAll()");
        List<CompanySentiment> result = sentimentUseCase.analyzeAll();
        log.debug("analyzeAll() | return={} sentiments", result.size());
        return ResponseEntity.ok(result);
    }
}

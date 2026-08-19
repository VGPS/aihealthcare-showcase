package com.wgblackmon.aihealthcare.web.controller;

import com.wgblackmon.aihealthcare.domain.marketanalysis.AffectedCompany;
import com.wgblackmon.aihealthcare.domain.marketanalysis.ImpactAssessment;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigest;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestEntry;
import com.wgblackmon.aihealthcare.domain.marketanalysis.MarketDigestService;
import com.wgblackmon.aihealthcare.domain.marketanalysis.NewsCategory;
import com.wgblackmon.aihealthcare.web.dto.MarketDigestEntryResponse;
import com.wgblackmon.aihealthcare.web.dto.MarketDigestResponse;
import com.wgblackmon.aihealthcare.web.dto.MarketDigestSummary;
import com.wgblackmon.aihealthcare.web.dto.WeeklyRollupResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing market digest query endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/market-digest/latest} — most recent digest (404 if none)</li>
 *   <li>{@code GET /api/market-digest/{date}} — digest for a specific date (404 if none)</li>
 *   <li>{@code GET /api/market-digest?from=&to=} — list of summaries, optionally filtered
 *       by date range; returns all digests when parameters are omitted</li>
 * </ul>
 *
 * <p>All endpoints require authentication ({@code /api/**} rule in SecurityConfig).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Slf4j
@RestController
@RequestMapping("/api/market-digest")
public class MarketDigestController {

    private final MarketDigestService marketDigestService;

    public MarketDigestController(MarketDigestService marketDigestService) {
        log.debug("MarketDigestController() | marketDigestService={}", marketDigestService);
        this.marketDigestService = marketDigestService;
        log.debug("MarketDigestController() | return=void");
    }

    /**
     * Returns the most recently generated market digest.
     *
     * @return 200 with digest body, or 404 if no digest has been generated yet
     */
    @GetMapping("/latest")
    public ResponseEntity<MarketDigestResponse> getLatest() {
        log.debug("getLatest()");

        Optional<MarketDigest> digest = marketDigestService.findLatest();

        if (digest.isEmpty()) {
            log.debug("getLatest() | return=404");
            return ResponseEntity.notFound().build();
        }

        MarketDigestResponse response = toResponse(digest.get());
        log.debug("getLatest() | return=200, date={}", response.date());
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the digest for a specific date.
     *
     * @param date the target date in {@code yyyy-MM-dd} format
     * @return 200 with digest body, or 404 if no digest exists for that date
     */
    @GetMapping("/{date}")
    public ResponseEntity<MarketDigestResponse> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("getByDate() | date={}", date);

        Optional<MarketDigest> digest = marketDigestService.findByDate(date);

        if (digest.isEmpty()) {
            log.debug("getByDate() | return=404");
            return ResponseEntity.notFound().build();
        }

        MarketDigestResponse response = toResponse(digest.get());
        log.debug("getByDate() | return=200");
        return ResponseEntity.ok(response);
    }

    /**
     * Returns a list of digest summaries, optionally filtered by date range.
     *
     * @param from optional start date (inclusive); omit for no lower bound
     * @param to   optional end date (inclusive); omit for no upper bound
     * @return 200 with (possibly empty) list of summaries
     */
    @GetMapping
    public ResponseEntity<List<MarketDigestSummary>> getAll(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        log.debug("getAll() | from={}, to={}", from, to);

        List<MarketDigest> all = marketDigestService.findAll();
        List<MarketDigestSummary> summaries = new ArrayList<>();

        for (MarketDigest digest : all) {
            if (from != null && digest.date().isBefore(from)) {
                continue;
            }
            if (to != null && digest.date().isAfter(to)) {
                continue;
            }
            summaries.add(new MarketDigestSummary(
                    digest.date(),
                    digest.generatedAt(),
                    digest.entries().size()
            ));
        }

        log.debug("getAll() | return=200, count={}", summaries.size());
        return ResponseEntity.ok(summaries);
    }

    /**
     * Returns a weekly rollup aggregating all digests for the 7-day window starting on
     * {@code weekOf} (inclusive) through {@code weekOf + 6 days} (inclusive).
     *
     * <p>Results include per-day summaries and a breakdown of qualifying entries
     * by news category.
     *
     * @param weekOf the first day of the target week in {@code yyyy-MM-dd} format (required)
     * @return 200 with the weekly rollup
     */
    @GetMapping("/weekly-rollup")
    public ResponseEntity<WeeklyRollupResponse> getWeeklyRollup(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekOf) {
        log.debug("getWeeklyRollup() | weekOf={}", weekOf);

        LocalDate weekEnd = weekOf.plusDays(6);
        List<MarketDigest> digests = marketDigestService.findByDateRange(weekOf, weekEnd);

        List<MarketDigestSummary> dailySummaries = new ArrayList<>();
        int[] categoryCounts = new int[NewsCategory.values().length];
        int totalEntries = 0;

        for (MarketDigest digest : digests) {
            dailySummaries.add(new MarketDigestSummary(
                    digest.date(),
                    digest.generatedAt(),
                    digest.entries().size()
            ));
            for (MarketDigestEntry entry : digest.entries()) {
                totalEntries++;
                int ordinal = entry.category().ordinal();
                categoryCounts[ordinal]++;
            }
        }

        List<WeeklyRollupResponse.CategoryCount> byCategory = new ArrayList<>();
        NewsCategory[] categories = NewsCategory.values();
        for (int i = 0; i < categories.length; i++) {
            if (categoryCounts[i] > 0) {
                byCategory.add(new WeeklyRollupResponse.CategoryCount(
                        categories[i].name(), categoryCounts[i]));
            }
        }

        WeeklyRollupResponse response = new WeeklyRollupResponse(
                weekOf, weekEnd, totalEntries, dailySummaries, byCategory);

        log.debug("getWeeklyRollup() | return=200, totalEntries={}", totalEntries);
        return ResponseEntity.ok(response);
    }

    // ─── mapping helpers ────────────────────────────────────────────────────

    private MarketDigestResponse toResponse(MarketDigest digest) {
        List<MarketDigestEntryResponse> entryResponses = new ArrayList<>();
        for (MarketDigestEntry entry : digest.entries()) {
            entryResponses.add(toEntryResponse(entry));
        }
        return new MarketDigestResponse(
                digest.date(),
                digest.generatedAt(),
                entryResponses.size(),
                entryResponses
        );
    }

    private MarketDigestEntryResponse toEntryResponse(MarketDigestEntry entry) {
        List<MarketDigestEntryResponse.ImpactAssessmentResponse> assessments = new ArrayList<>();
        for (ImpactAssessment a : entry.impactAssessments()) {
            assessments.add(new MarketDigestEntryResponse.ImpactAssessmentResponse(
                    a.dimension().name(),
                    a.direction().name(),
                    a.rationale()
            ));
        }

        List<MarketDigestEntryResponse.AffectedCompanyResponse> companies = new ArrayList<>();
        for (AffectedCompany c : entry.affectedCompanies()) {
            companies.add(new MarketDigestEntryResponse.AffectedCompanyResponse(
                    c.name(),
                    c.tickerSymbol(),
                    c.role(),
                    c.peerGroup() != null ? c.peerGroup().name() : null
            ));
        }

        return new MarketDigestEntryResponse(
                entry.newsItem().headline(),
                entry.newsItem().summary(),
                entry.newsItem().sourceUrls(),
                entry.newsItem().publishedAt(),
                entry.newsItem().category().name(),
                entry.newsItem().dealSizeUsd(),
                entry.factClassification().name(),
                entry.rank().value(),
                assessments,
                companies
        );
    }
}

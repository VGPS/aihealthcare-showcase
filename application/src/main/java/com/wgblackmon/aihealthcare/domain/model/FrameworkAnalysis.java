package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Deep competitive analysis of one company's healthcare AI framework.
 *
 * <p>Produced by LLM analysis of the full article corpus for a company,
 * scoring across six dimensions (technical maturity, clinical validation,
 * regulatory positioning, platform strategy, market momentum, developer
 * experience). Includes narrative assessments and evidence-based bullet
 * points for strengths, weaknesses, and recent developments.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
public record FrameworkAnalysis(
        String companySlug,
        String companyName,
        String overallAssessment,
        List<FrameworkDimension> dimensions,
        List<String> strengths,
        List<String> weaknesses,
        List<String> recentDevelopments,
        int overallScore,
        int articleCount,
        Instant analyzedAt
) {

    /**
     * Compact constructor — validates required fields and defensively copies lists.
     */
    public FrameworkAnalysis {
        if (companySlug == null || companySlug.isBlank()) {
            throw new IllegalArgumentException("companySlug must not be blank");
        }
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("companyName must not be blank");
        }
        if (overallAssessment == null || overallAssessment.isBlank()) {
            throw new IllegalArgumentException("overallAssessment must not be blank");
        }
        if (dimensions == null) {
            throw new IllegalArgumentException("dimensions must not be null");
        }
        dimensions = new ArrayList<>(dimensions);
        strengths = strengths != null ? new ArrayList<>(strengths) : new ArrayList<>();
        weaknesses = weaknesses != null ? new ArrayList<>(weaknesses) : new ArrayList<>();
        recentDevelopments = recentDevelopments != null ? new ArrayList<>(recentDevelopments) : new ArrayList<>();
        if (analyzedAt == null) {
            throw new IllegalArgumentException("analyzedAt must not be null");
        }
    }
}

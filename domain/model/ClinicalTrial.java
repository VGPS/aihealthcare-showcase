package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable domain record representing a clinical trial relevant to
 * AI in healthcare, harvested from ClinicalTrials.gov.
 *
 * <p>Each trial carries structured metadata (NCT identifier, sponsor,
 * phase, recruitment status, conditions) that plain {@link NewsArticle}
 * records cannot express. The {@code nctId} is the ClinicalTrials.gov
 * unique identifier (e.g. "NCT06123456") used for deduplication.
 *
 * @param trialId             unique internal identifier (UUID)
 * @param nctId               ClinicalTrials.gov identifier (e.g. "NCT06123456")
 * @param title               brief title of the trial
 * @param sponsor             lead sponsor organisation (nullable)
 * @param status              overall recruitment status
 * @param phase               trial phase (nullable for observational studies)
 * @param conditions          medical conditions studied (defensive copy)
 * @param briefSummary        short description from the trial record (nullable)
 * @param sourceUrl           link to the trial on ClinicalTrials.gov
 * @param studyType           "INTERVENTIONAL" or "OBSERVATIONAL" (nullable)
 * @param startDate           trial start date (nullable)
 * @param discoveredAt        when our system first discovered this trial
 * @param aiHealthcareKeywords extracted keywords relevant to AI/healthcare
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-23
 * @updated 2026-07-23
 */
public record ClinicalTrial(
        String trialId,
        String nctId,
        String title,
        String sponsor,
        ClinicalTrialStatus status,
        ClinicalTrialPhase phase,
        List<String> conditions,
        String briefSummary,
        String sourceUrl,
        String studyType,
        Instant startDate,
        Instant discoveredAt,
        List<String> aiHealthcareKeywords
) {

    /** Compact constructor — validates required fields and defensive-copies lists. */
    public ClinicalTrial {
        if (trialId == null || trialId.isBlank()) {
            throw new IllegalArgumentException("trialId must not be blank");
        }
        if (nctId == null || nctId.isBlank()) {
            throw new IllegalArgumentException("nctId must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("sourceUrl must not be blank");
        }
        if (discoveredAt == null) {
            throw new IllegalArgumentException("discoveredAt must not be null");
        }
        conditions = conditions == null
                ? List.of()
                : List.copyOf(conditions);
        aiHealthcareKeywords = aiHealthcareKeywords == null
                ? List.of()
                : List.copyOf(aiHealthcareKeywords);
    }
}

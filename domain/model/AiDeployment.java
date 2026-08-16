package com.wgblackmon.aihealthcare.domain.model;

import java.time.LocalDate;

/**
 * Immutable domain record representing an AI deployment at a health system,
 * used in the AI Accountability Tracker.
 *
 * <p>Covers four distinct domains: CLINICAL (ambient documentation, imaging AI),
 * REVENUE_CYCLE (denials combat, autonomous coding, appeals targeting),
 * FINANCIAL_SCORING (charity-care eligibility scoring, propensity-to-pay AI),
 * and RESEARCH (foundation models, genomics platforms). Financial scoring is
 * the least-covered and most ethically loaded category — always rendered distinctly.
 *
 * <p>{@code scaleMetric} is free-text describing deployment breadth
 * (e.g. "4,000+ providers", "$40M+ savings", "80% of radiology coding automated").
 * Vendor-reported metrics should be labeled as such in UI display.
 *
 * @param id             unique identifier
 * @param healthSystemId FK to {@link HealthSystem#id()}
 * @param vendor         AI vendor or "Internal"
 * @param product        specific product or tool name
 * @param domain         CLINICAL, REVENUE_CYCLE, FINANCIAL_SCORING, or RESEARCH
 * @param scaleMetric    free-text deployment scale description
 * @param sourceUrl      primary source URL
 * @param deployedDate   approximate deployment date (nullable)
 *
 * @author  Bill Blackmon
 * @since   2026-08-16
 * @updated 2026-08-16
 */
public record AiDeployment(
        String id,
        String healthSystemId,
        String vendor,
        String product,
        String domain,
        String scaleMetric,
        String sourceUrl,
        LocalDate deployedDate
) {}

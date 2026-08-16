package com.wgblackmon.aihealthcare.domain.model;

import java.time.LocalDate;

/**
 * Immutable domain record representing a federal or state enforcement action
 * against a health system, used in the AI Accountability Tracker.
 *
 * <p>{@code entityNameAtTime} preserves the legal name at the time of action —
 * critical because predecessor entities differ from current names (e.g.
 * "Adventist Health System" for an AdventHealth FCA action, "Dignity Health"
 * for a CommonSpirit action). Never conflate successor names with historical actions.
 *
 * <p>All amounts are the largest single publicly reported resolution per system;
 * many span different legal theories with no admission of liability.
 *
 * @param id                unique identifier (kebab-case slug)
 * @param healthSystemId    FK to {@link HealthSystem#id()}
 * @param entityNameAtTime  legal entity name at the time of the action
 * @param agency            enforcing body: DOJ, HHS_OIG, STATE_AG, FTC, CMS
 * @param theory            legal theory: FALSE_CLAIMS_ACT, STARK, AKS, CONSUMER_PROTECTION, CMP, CONTROLLED_SUBSTANCES
 * @param amountUsd         settlement or penalty amount in USD (nullable)
 * @param settlementDate    date of resolution (nullable)
 * @param description       plain-English description of the action
 * @param sourceUrl         primary source URL (DOJ press release, AG announcement, etc.)
 *
 * @author  Bill Blackmon
 * @since   2026-08-16
 * @updated 2026-08-16
 */
public record EnforcementAction(
        String id,
        String healthSystemId,
        String entityNameAtTime,
        String agency,
        String theory,
        Long amountUsd,
        LocalDate settlementDate,
        String description,
        String sourceUrl
) {}

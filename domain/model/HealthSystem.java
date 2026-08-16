package com.wgblackmon.aihealthcare.domain.model;

import java.util.List;

/**
 * Immutable domain record representing one of the 15 largest U.S. health systems,
 * used as the master entity for the AI Accountability Tracker.
 *
 * <p>The {@code id} is a kebab-case slug (e.g. "kaiser", "hca", "commonspirit")
 * that serves as the stable identifier across enforcement, deployment, and
 * denial records. {@code aliases} captures historical and subsidiary entity names
 * critical for disambiguation — e.g. "Adventist Health System" predates "AdventHealth".
 *
 * @param id            kebab-case slug (e.g. "kaiser", "hca")
 * @param canonicalName current legal display name
 * @param aliases       historical entity names for disambiguation
 * @param hqState       two-letter state code of headquarters
 * @param systemType    NONPROFIT, FOR_PROFIT, or ACADEMIC
 *
 * @author  Bill Blackmon
 * @since   2026-08-16
 * @updated 2026-08-16
 */
public record HealthSystem(
        String id,
        String canonicalName,
        List<String> aliases,
        String hqState,
        String systemType
) {}

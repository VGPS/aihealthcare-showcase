package com.wgblackmon.aihealthcare.domain.model;

/**
 * Production-effort tier for an editorial calendar item.
 *
 * <p>Calibrates expected writing time:
 * <ul>
 *   <li>S (Short) — 2–4 hours, tightly focused explainer, 800–1,200 words</li>
 *   <li>M (Medium) — 4–8 hours, multi-source analysis, 1,200–2,000 words</li>
 *   <li>L (Large) — 8–14 hours, deep-dive framework or original template, 2,000+ words</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public enum EditorialEffort {
    S,
    M,
    L
}

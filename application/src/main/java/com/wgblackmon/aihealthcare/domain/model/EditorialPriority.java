package com.wgblackmon.aihealthcare.domain.model;

/**
 * Priority tier for an editorial calendar item.
 *
 * <p>P0 items are tied to current laws, active enforcement, or annual deadlines
 * and bypass search-volume gating. P1 items have steady strategic value and
 * proceed to drafting before full validation. P2 evergreen items require
 * first-party search data before locking production investment.
 *
 * <p>Alphabetical enum ordering (P0 &lt; P1 &lt; P2) is intentional — JPA
 * {@code ORDER BY priority_tier ASC} yields the correct priority sequence.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public enum EditorialPriority {
    P0,
    P1,
    P2
}

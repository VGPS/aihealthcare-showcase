package com.wgblackmon.aihealthcare.domain.model;

/**
 * Search-demand signal tier for an editorial calendar item.
 *
 * <p>Used to distinguish time-sensitive regulatory items (HOT) from
 * repeatable strategic topics (STEADY) and durable reference content (EVERGREEN).
 * P2 evergreen items require first-party search validation before committing
 * production effort.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
public enum EditorialDemandSignal {
    HOT,
    STEADY,
    EVERGREEN
}

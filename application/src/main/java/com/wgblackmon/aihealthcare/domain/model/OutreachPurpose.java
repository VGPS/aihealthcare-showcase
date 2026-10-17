package com.wgblackmon.aihealthcare.domain.model;

/**
 * Reason an outreach record was created for a company.
 *
 * <p>EMPLOYMENT and SUBSCRIPTION are auto-synced for every directory company by
 * {@link com.wgblackmon.aihealthcare.domain.service.OutreachSyncService}. ACQUISITION
 * is manually added per target and is intentionally excluded from that sync.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-17
 */
public enum OutreachPurpose {
    EMPLOYMENT,
    SUBSCRIPTION,
    ACQUISITION
}

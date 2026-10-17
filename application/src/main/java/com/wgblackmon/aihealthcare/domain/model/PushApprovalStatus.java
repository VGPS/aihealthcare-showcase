package com.wgblackmon.aihealthcare.domain.model;

/**
 * Lifecycle status of a held push delivery awaiting admin review.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-18
 * @updated 2026-10-18
 */
public enum PushApprovalStatus {

    /** Delivery is held; awaiting an admin decision. */
    PENDING,

    /** An admin approved delivery; the artifact was sent. */
    APPROVED,

    /** An admin rejected delivery; the artifact was never sent. */
    REJECTED
}

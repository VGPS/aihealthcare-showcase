package com.wgblackmon.aihealthcare.domain.model;

/**
 * Actions recorded in the enterprise data audit trail.
 *
 * <p>Every meaningful lifecycle event — submission, denial, fetch, render,
 * completion, download — is captured as an append-only audit row so access
 * patterns are queryable long after artifacts expire.
 *
 * @author  Bill Blackmon
 * @version 1.1
 * @since   2026-09-08
 * @updated 2026-10-18 — PUSH_HOLD/PUSH_REJECT added for the approval gate
 */
public enum DataAccessAction {

    SUBMIT,
    TIER_DENY,
    QUOTA_DENY,
    CONCURRENCY_DENY,
    PLAN,
    FETCH,
    RENDER,
    COMPLETE,
    FAIL,
    CANCEL,
    DOWNLOAD_ARTIFACT,
    DOWNLOAD_LOG,

    SCHEDULE_CREATE,
    SCHEDULE_UPDATE,
    SCHEDULE_DELETE,
    SCHEDULE_FIRE,
    PUSH_SEND,
    PUSH_FAIL,
    SCHEDULE_DEACTIVATE,
    LINK_REDEEM,

    /** Delivery held pending admin review (approval gate active for the schedule). */
    PUSH_HOLD,

    /** An admin rejected a held delivery — the artifact was never sent. */
    PUSH_REJECT
}

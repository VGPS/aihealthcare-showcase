package com.wgblackmon.aihealthcare.domain.model;

/**
 * Actions recorded in the enterprise data audit trail.
 *
 * <p>Every meaningful lifecycle event — submission, denial, fetch, render,
 * completion, download — is captured as an append-only audit row so access
 * patterns are queryable long after artifacts expire.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08 — ED-2 push schedule actions added
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
    LINK_REDEEM
}

package com.wgblackmon.aihealthcare.domain.model;

/**
 * Lifecycle status of outreach to an individual contact.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-03
 * @updated 2026-10-03
 */
public enum ContactStatus {
    IDENTIFIED,
    REACHED_OUT,
    RESPONDED,
    MEETING_SCHEDULED,
    DECLINED
}

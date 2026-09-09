package com.wgblackmon.aihealthcare.domain.model;

/**
 * Distinguishes the two trigger paths for an enterprise data job.
 *
 * <p>{@code PULL} is an on-demand request from the console or API.
 * {@code PUSH} is a scheduled delivery triggered by the DB sweeper (ED-2).
 * Both share the same execution core.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public enum DataJobMode {

    PULL,
    PUSH
}

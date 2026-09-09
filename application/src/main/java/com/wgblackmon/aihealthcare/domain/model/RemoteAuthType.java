package com.wgblackmon.aihealthcare.domain.model;

/**
 * Authentication method for a customer-side remote connection.
 *
 * <p>{@code API_KEY_HEADER} sends the resolved secret as a named HTTP header.
 * {@code BEARER} sends it as {@code Authorization: Bearer <value>}.
 * {@code NONE} sends no credentials.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public enum RemoteAuthType {

    NONE,
    API_KEY_HEADER,
    BEARER
}

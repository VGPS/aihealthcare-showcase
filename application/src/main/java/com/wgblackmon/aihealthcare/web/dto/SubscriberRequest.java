package com.wgblackmon.aihealthcare.web.dto;

/**
 * Request body for adding a new newsletter subscriber.
 *
 * @param email The subscriber's email address.  Must be unique across all subscribers.
 * @param name  The subscriber's display name.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
public record SubscriberRequest(
        String email,
        String name
) {}

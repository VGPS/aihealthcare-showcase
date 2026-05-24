package com.wgblackmon.aihealthcare.web.dto;

import java.time.Instant;

/**
 * Response body representing a newsletter subscriber.
 *
 * @param email        The subscriber's email address.
 * @param name         The subscriber's display name.
 * @param active       Whether the subscriber currently receives mailings.
 * @param subscribedAt Timestamp of when the subscription was created.
 * @param tier         Subscription tier (FREE, PREMIUM, ENTERPRISE).
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-05-23
 */
public record SubscriberResponse(
        String  email,
        String  name,
        boolean active,
        Instant subscribedAt,
        String  tier
) {}

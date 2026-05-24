package com.wgblackmon.aihealthcare.web.dto;

/**
 * Request body for creating a Stripe Checkout Session.
 *
 * @param email   The subscriber's email address — pre-filled on the Stripe payment page.
 * @param priceId The Stripe Price ID for the desired subscription tier.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-23
 * @updated 2026-05-23
 */
public record CheckoutRequest(
        String email,
        String priceId
) {}

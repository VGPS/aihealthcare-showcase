package com.wgblackmon.aihealthcare.web.dto;

/**
 * Response body containing the Stripe-hosted Checkout Session URL.
 *
 * <p>The caller redirects the subscriber to this URL to complete payment.
 * After successful payment, Stripe fires a {@code checkout.session.completed}
 * webhook event that updates the subscriber's tier locally.
 *
 * @param checkoutUrl The Stripe-hosted payment page URL.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-05-23
 * @updated 2026-05-23
 */
public record CheckoutResponse(
        String checkoutUrl
) {}

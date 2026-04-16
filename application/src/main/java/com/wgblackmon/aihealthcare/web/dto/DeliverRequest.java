package com.wgblackmon.aihealthcare.web.dto;

/**
 * Request body for the manual newsletter delivery endpoint.
 *
 * @param runId The ID of the newsletter run to deliver to all active subscribers.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-13
 * @updated 2026-04-13
 */
public record DeliverRequest(String runId) {}

package com.wgblackmon.aihealthcare.web.dto;

/**
 * vis-network edge DTO for the company relationship graph.
 *
 * <p>{@code from} and {@code to} must match existing {@link VisNode#id()} values.
 * {@code color} is a hex string; the client wraps it in vis-network's
 * {@code {color, highlight, hover}} object. {@code value} sets edge width
 * via vis-network scaling. No {@code title} is set — detail is shown in the
 * sidebar panel on click.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-30
 * @updated 2026-08-30
 */
public record VisEdge(
        String id,
        String from,
        String to,
        String label,
        String color,
        double value
) {}

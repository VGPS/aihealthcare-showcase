package com.wgblackmon.aihealthcare.web.dto;

/**
 * vis-network node DTO for the company relationship graph.
 *
 * <p>Field names match the vis-network DataSet schema exactly: {@code id} is
 * the node identifier, {@code label} is the displayed text, and {@code value}
 * drives proportional node sizing via vis-network's scaling option. No
 * {@code title} is set — detail is shown in the sidebar panel on click.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-30
 * @updated 2026-08-30
 */
public record VisNode(String id, String label, int value) {}

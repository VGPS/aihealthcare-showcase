package com.wgblackmon.aihealthcare.web.dto;

import java.util.List;

/**
 * Graph data response returned by {@code GET /api/v1/relationships/graph}.
 *
 * <p>Jackson serializes this as {@code {"nodes":[...],"edges":[...]}} — the
 * exact shape expected by vis-network's {@code new vis.Network(container, data, options)}
 * constructor when accessed as {@code data.nodes} and {@code data.edges}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-30
 * @updated 2026-08-30
 */
public record RelationshipGraphResponse(List<VisNode> nodes, List<VisEdge> edges) {}

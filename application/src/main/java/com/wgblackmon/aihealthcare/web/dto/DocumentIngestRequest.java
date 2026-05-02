package com.wgblackmon.aihealthcare.web.dto;

/**
 * Request body for the POST /api/v1/documents/ingest endpoint.
 *
 * @param directory   Absolute path to the directory containing documents to ingest.
 * @param sourceLabel Human-readable label applied to every chunk from this run.
 * @param chunkSize   Maximum character length per chunk; {@code null} defaults to 1000
 *                    in the controller.
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-27
 * @updated 2026-04-27
 */
public record DocumentIngestRequest(
        String  directory,
        String  sourceLabel,
        Integer chunkSize
) {}

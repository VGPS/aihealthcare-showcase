package com.wgblackmon.aihealthcare.domain.model;

import java.time.Instant;

/**
 * Metadata for a generated enterprise data artifact on disk.
 *
 * <p>The artifact itself is stored by {@link
 * com.wgblackmon.aihealthcare.domain.port.outbound.DataArtifactPort} and
 * confined to a single directory tree. This record carries enough information
 * for the controller to stream the file and set download headers.
 *
 * @param jobId     the job that produced this artifact
 * @param format    export format (CSV, JSON, etc.)
 * @param fileName  server-chosen filename ({@code {jobId}.{ext}})
 * @param byteSize  artifact size in bytes
 * @param sha256    hex-encoded SHA-256 digest of the content
 * @param createdAt when the artifact was written
 * @param expiresAt when the retention sweeper may delete it
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public record DataArtifact(
        String jobId,
        ExportFormat format,
        String fileName,
        long byteSize,
        String sha256,
        Instant createdAt,
        Instant expiresAt
) {
}

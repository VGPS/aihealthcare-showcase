package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DataArtifact;
import com.wgblackmon.aihealthcare.domain.model.ExportFormat;

import java.io.InputStream;

/**
 * Filesystem port for enterprise data artifacts (generated CSVs, JSONs, etc.).
 *
 * <p>All I/O is confined to a single directory tree via
 * {@code ConfinedFileStore}. File names are composed only of the UUID job id
 * and the format extension — no customer-supplied string reaches the
 * filesystem.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface DataArtifactPort {

    DataArtifact write(String jobId, ExportFormat format, byte[] content);

    InputStream read(String jobId);

    void delete(String jobId);

    boolean exists(String jobId);
}

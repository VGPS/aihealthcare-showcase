package com.wgblackmon.aihealthcare.domain.port.outbound;

/**
 * Filesystem port for per-job log files.
 *
 * <p>Each enterprise data job gets its own log file. The returned
 * {@link DataJobLog} is an appender that adapters use to record progress.
 * Reading supports tailing from a byte offset for the HTMX-polled
 * console pane.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface DataJobLogPort {

    DataJobLog open(String jobId);

    String read(String jobId, long fromByteOffset);

    void delete(String jobId);
}

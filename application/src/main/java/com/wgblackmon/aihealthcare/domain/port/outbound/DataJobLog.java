package com.wgblackmon.aihealthcare.domain.port.outbound;

/**
 * Appender interface handed to data source adapters so they can write
 * structured log lines into the per-job log file without knowing where
 * it lives on disk.
 *
 * <p>Every detail string must be sanitised before it reaches this interface;
 * the implementation applies {@code LogSanitizer} as a second safety net.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface DataJobLog {

    /**
     * Writes a structured INFO-level log line.
     *
     * @param phase              lifecycle phase (e.g. FETCH_START, RENDER_END)
     * @param detailKeyValuePairs additional key=value context (sanitised)
     */
    void phase(String phase, String detailKeyValuePairs);

    /**
     * Writes a WARN-level log line.
     */
    void warn(String phase, String detail);

    /**
     * Writes an ERROR-level log line with an exception.
     */
    void error(String phase, String detail, Throwable cause);

    /**
     * Returns the current byte offset of the log file, for tailing.
     */
    long byteOffset();
}

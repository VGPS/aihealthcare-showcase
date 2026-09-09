package com.wgblackmon.aihealthcare.domain.port.outbound;

import com.wgblackmon.aihealthcare.domain.model.DataFeed;
import com.wgblackmon.aihealthcare.domain.model.DataRequest;
import com.wgblackmon.aihealthcare.domain.model.DataSet;
import com.wgblackmon.aihealthcare.domain.model.DataSourceKind;

/**
 * The central abstraction of the enterprise data feed registry.
 *
 * <p>Each adapter represents one data feed (articles, legislation,
 * regulatory events, AI synthesis, customer-remote, etc.). Spring injects
 * all implementations as a {@code List}, and the domain service builds a
 * {@code Map} keyed on {@link #feedId()} at construction time. Adding a new
 * feed is one new adapter class and zero changes elsewhere.
 *
 * <p><strong>Ownership contract:</strong> an adapter reads — it never mutates
 * the corpus. It must honour {@code request.rowLimit()}. It must write a
 * {@code FETCH_START} and {@code FETCH_END} line to the supplied log. It
 * must never receive or construct raw SQL.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-09-08
 * @updated 2026-09-08
 */
public interface EnterpriseDataSourcePort {

    /** Stable feed identifier (e.g. "legislation"). */
    String feedId();

    /** Data source family. */
    DataSourceKind kind();

    /** Metadata describing this feed's capabilities and parameters. */
    DataFeed describe();

    /** Whether this adapter can handle the given request. */
    boolean supports(DataRequest request);

    /**
     * Fetches data for the request.
     *
     * @param request the validated, plan-resolved request
     * @param log     per-job log appender — write FETCH_START and FETCH_END
     * @return the result set (never null; may be empty)
     */
    DataSet fetch(DataRequest request, DataJobLog log);
}

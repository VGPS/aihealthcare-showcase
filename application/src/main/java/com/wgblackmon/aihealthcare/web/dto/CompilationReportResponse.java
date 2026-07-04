package com.wgblackmon.aihealthcare.web.dto;

import com.wgblackmon.aihealthcare.domain.model.CompilationReport;

import java.time.Instant;
import java.util.List;

/**
 * REST response record for a wiki compilation report.
 *
 * <p>Provides a concise view of the compilation outcome suitable for
 * JSON serialization.  The contradiction count is derived from the
 * domain record's list size rather than duplicating the full objects.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
public record CompilationReportResponse(
        Instant runStartedAt,
        Instant runCompletedAt,
        int articlesProcessed,
        List<String> pagesCreated,
        List<String> pagesUpdated,
        int contradictionCount,
        List<String> warnings
) {

    /**
     * Factory method to convert a domain {@link CompilationReport} to a response DTO.
     *
     * @param report the domain compilation report
     * @return a response record suitable for JSON serialization
     */
    public static CompilationReportResponse from(CompilationReport report) {
        return new CompilationReportResponse(
                report.runStartedAt(),
                report.runCompletedAt(),
                report.articlesProcessed(),
                report.pagesCreated(),
                report.pagesUpdated(),
                report.contradictionsFlagged().size(),
                report.warnings()
        );
    }
}

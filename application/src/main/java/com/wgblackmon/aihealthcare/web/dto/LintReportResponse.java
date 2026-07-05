package com.wgblackmon.aihealthcare.web.dto;

import com.wgblackmon.aihealthcare.domain.model.LintReport;

import java.time.Instant;
import java.util.List;

/**
 * Response DTO for wiki lint reports returned by the lint REST endpoints.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-05
 * @updated 2026-07-05
 */
public record LintReportResponse(
        Instant runStartedAt,
        Instant runCompletedAt,
        int totalPagesChecked,
        List<String> orphanedSlugs,
        List<String> brokenRefs,
        List<String> staleSlugs,
        List<String> missingProvenance,
        List<String> warnings,
        int totalIssues
) {
    /**
     * Factory method to create a response from a domain {@link LintReport}.
     *
     * @param report the domain lint report
     * @return the response DTO
     */
    public static LintReportResponse from(LintReport report) {
        int totalIssues = report.orphanedSlugs().size()
                + report.brokenRefs().size()
                + report.staleSlugs().size()
                + report.missingProvenance().size();

        return new LintReportResponse(
                report.runStartedAt(),
                report.runCompletedAt(),
                report.totalPagesChecked(),
                report.orphanedSlugs(),
                report.brokenRefs(),
                report.staleSlugs(),
                report.missingProvenance(),
                report.warnings(),
                totalIssues
        );
    }
}

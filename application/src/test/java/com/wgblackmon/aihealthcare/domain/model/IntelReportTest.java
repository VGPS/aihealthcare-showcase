package com.wgblackmon.aihealthcare.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link IntelReport} domain record validations.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
class IntelReportTest {

    private static final String REPORT_ID = "rpt-001";
    private static final String QUERY = "Anthropic healthcare AI";
    private static final String HTML = "<h2>Executive Summary</h2><p>Content</p>";
    private static final String EMAIL = "user@example.com";
    private static final Instant NOW = Instant.now();

    @Test
    void validReport_createsSuccessfully() {
        IntelReport report = new IntelReport(REPORT_ID, QUERY, HTML, 5, EMAIL, NOW, List.of());

        assertThat(report.reportId()).isEqualTo(REPORT_ID);
        assertThat(report.query()).isEqualTo(QUERY);
        assertThat(report.htmlContent()).isEqualTo(HTML);
        assertThat(report.sourceCount()).isEqualTo(5);
        assertThat(report.userEmail()).isEqualTo(EMAIL);
        assertThat(report.generatedAt()).isEqualTo(NOW);
    }

    @Test
    void nullReportId_throws() {
        assertThatThrownBy(() -> new IntelReport(null, QUERY, HTML, 5, EMAIL, NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reportId");
    }

    @Test
    void blankQuery_throws() {
        assertThatThrownBy(() -> new IntelReport(REPORT_ID, "  ", HTML, 5, EMAIL, NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query");
    }

    @Test
    void blankHtmlContent_throws() {
        assertThatThrownBy(() -> new IntelReport(REPORT_ID, QUERY, "", 5, EMAIL, NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("htmlContent");
    }

    @Test
    void nullUserEmail_throws() {
        assertThatThrownBy(() -> new IntelReport(REPORT_ID, QUERY, HTML, 5, null, NOW, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userEmail");
    }

    @Test
    void nullSources_defaultsToEmptyList() {
        IntelReport report = new IntelReport(REPORT_ID, QUERY, HTML, 5, EMAIL, NOW, null);

        assertThat(report.sources()).isNotNull();
        assertThat(report.sources()).isEmpty();
    }

    @Test
    void nullGeneratedAt_throws() {
        assertThatThrownBy(() -> new IntelReport(REPORT_ID, QUERY, HTML, 5, EMAIL, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("generatedAt");
    }
}

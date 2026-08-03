package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code intel_reports} table.
 *
 * <p>This is a mutable infrastructure class that mirrors the immutable
 * {@link com.wgblackmon.aihealthcare.domain.model.IntelReport} domain record.
 * All mapping between the two types happens inside {@link IntelReportAdapter}.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-03
 * @updated 2026-08-03
 */
@Entity
@Table(name = "intel_reports")
public class IntelReportEntity {

    @Id
    @Column(name = "report_id")
    private String reportId;

    @Column(nullable = false)
    private String query;

    @Column(name = "html_content", columnDefinition = "TEXT", nullable = false)
    private String htmlContent;

    @Column(name = "source_count")
    private int sourceCount;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "sources_data", columnDefinition = "TEXT")
    private String sourcesData;

    /** Required no-arg constructor for JPA. */
    public IntelReportEntity() {}

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }

    public int getSourceCount() { return sourceCount; }
    public void setSourceCount(int sourceCount) { this.sourceCount = sourceCount; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }

    public String getSourcesData() { return sourcesData; }
    public void setSourcesData(String sourcesData) { this.sourcesData = sourcesData; }
}

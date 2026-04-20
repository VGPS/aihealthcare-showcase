package com.wgblackmon.aihealthcare.infrastructure.persistence;

import com.wgblackmon.aihealthcare.domain.model.NewsletterRunStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * JPA entity representing a row in the {@code newsletter_runs} table.
 *
 * <p>This is a mutable infrastructure class that mirrors the immutable
 * {@link com.wgblackmon.aihealthcare.domain.model.NewsletterRun} domain record.
 * All mapping between the two types happens inside {@link NewsletterRunAdapter}.
 *
 * <p>The {@code html_content} and {@code plain_text_content} columns are
 * {@code CLOB} to accommodate full newsletter HTML and plain-text bodies
 * without length restrictions.  These are read by the subscriber archive
 * endpoint ({@code GET /api/v1/runs/{runId}}) for display in the web UI.
 *
 * <p>The {@code status} column stores the {@link NewsletterRunStatus} enum
 * name as a {@code VARCHAR} (e.g. "DRAFT", "SENT", "ARCHIVED") so that
 * values are human-readable in the H2 console and future Postgres queries.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-11
 * @updated 2026-04-11
 */
@Entity
@Table(name = "newsletter_runs")
public class NewsletterRunEntity {

    @Id
    @Column(name = "run_id")
    private String runId;

    private String title;

    private LocalDate weekOf;

    @Column(name = "html_content", columnDefinition = "TEXT")
    private String htmlContent;

    @Column(name = "plain_text_content", columnDefinition = "TEXT")
    private String plainTextContent;

    @Enumerated(EnumType.STRING)
    private NewsletterRunStatus status;

    private Instant generatedAt;

    /** Required no-arg constructor for JPA. */
    public NewsletterRunEntity() {}

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public LocalDate getWeekOf() { return weekOf; }
    public void setWeekOf(LocalDate weekOf) { this.weekOf = weekOf; }

    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }

    public String getPlainTextContent() { return plainTextContent; }
    public void setPlainTextContent(String plainTextContent) { this.plainTextContent = plainTextContent; }

    public NewsletterRunStatus getStatus() { return status; }
    public void setStatus(NewsletterRunStatus status) { this.status = status; }

    public Instant getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(Instant generatedAt) { this.generatedAt = generatedAt; }
}

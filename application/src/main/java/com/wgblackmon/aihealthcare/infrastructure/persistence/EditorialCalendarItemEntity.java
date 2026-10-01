package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * JPA entity for the {@code editorial_calendar_items} table.
 *
 * <p>The {@code id} column is a kebab-case slug (e.g.
 * {@code texas-traiga-healthcare-ai-disclosure}) that also serves as the stem
 * of the published {@code /insights/{slug}.html} filename.
 *
 * <p>Multi-value fields:
 * <ul>
 *   <li>{@code audiences} — pipe-delimited string (consistent with other
 *       multi-value fields in this project)</li>
 *   <li>{@code primarySourcesJson} — JSON-serialized
 *       {@code List<EditorialSource>} stored as TEXT</li>
 * </ul>
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-10-01
 * @updated 2026-10-01
 */
@Entity
@Table(name = "editorial_calendar_items")
public class EditorialCalendarItemEntity {

    @Id
    @Column(name = "id", length = 128)
    private String id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String hook;

    @Column(name = "theme", nullable = false, length = 64)
    private String theme;

    @Column(name = "demand_signal", length = 32)
    private String demandSignal;

    @Column(name = "priority_tier", nullable = false, length = 8)
    private String priorityTier;

    @Column(name = "effort", length = 8)
    private String effort;

    @Column(name = "format", length = 64)
    private String format;

    @Column(name = "publish_window_start")
    private LocalDate publishWindowStart;

    @Column(name = "publish_window_end")
    private LocalDate publishWindowEnd;

    @Column(name = "preferred_date", nullable = false)
    private LocalDate preferredDate;

    @Column(name = "cta", columnDefinition = "TEXT")
    private String cta;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "last_verified")
    private LocalDate lastVerified;

    @Column(name = "audiences", columnDefinition = "TEXT")
    private String audiences;

    @Column(name = "primary_sources_json", columnDefinition = "TEXT")
    private String primarySourcesJson;

    protected EditorialCalendarItemEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getHook() { return hook; }
    public void setHook(String hook) { this.hook = hook; }

    public String getTheme() { return theme; }
    public void setTheme(String theme) { this.theme = theme; }

    public String getDemandSignal() { return demandSignal; }
    public void setDemandSignal(String demandSignal) { this.demandSignal = demandSignal; }

    public String getPriorityTier() { return priorityTier; }
    public void setPriorityTier(String priorityTier) { this.priorityTier = priorityTier; }

    public String getEffort() { return effort; }
    public void setEffort(String effort) { this.effort = effort; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public LocalDate getPublishWindowStart() { return publishWindowStart; }
    public void setPublishWindowStart(LocalDate publishWindowStart) { this.publishWindowStart = publishWindowStart; }

    public LocalDate getPublishWindowEnd() { return publishWindowEnd; }
    public void setPublishWindowEnd(LocalDate publishWindowEnd) { this.publishWindowEnd = publishWindowEnd; }

    public LocalDate getPreferredDate() { return preferredDate; }
    public void setPreferredDate(LocalDate preferredDate) { this.preferredDate = preferredDate; }

    public String getCta() { return cta; }
    public void setCta(String cta) { this.cta = cta; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getLastVerified() { return lastVerified; }
    public void setLastVerified(LocalDate lastVerified) { this.lastVerified = lastVerified; }

    public String getAudiences() { return audiences; }
    public void setAudiences(String audiences) { this.audiences = audiences; }

    public String getPrimarySourcesJson() { return primarySourcesJson; }
    public void setPrimarySourcesJson(String primarySourcesJson) { this.primarySourcesJson = primarySourcesJson; }
}

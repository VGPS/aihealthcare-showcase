package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * Singleton JPA entity storing the newsletter auto-send override setting.
 *
 * <p>A single row (id=1) persists whether today's auto-send is overridden.
 * When {@code autoSendOverride} is true and {@code overrideDate} matches
 * today, the daily scheduler skips delivery and leaves the draft for
 * manual review.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-04
 * @updated 2026-08-04
 */
@Entity
@Table(name = "newsletter_send_settings")
public class NewsletterSendSettingsEntity {

    @Id
    private Long id = 1L;

    @Column(name = "auto_send_override", nullable = false)
    private boolean autoSendOverride = false;

    @Column(name = "override_date")
    private LocalDate overrideDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isAutoSendOverride() {
        return autoSendOverride;
    }

    public void setAutoSendOverride(boolean autoSendOverride) {
        this.autoSendOverride = autoSendOverride;
    }

    public LocalDate getOverrideDate() {
        return overrideDate;
    }

    public void setOverrideDate(LocalDate overrideDate) {
        this.overrideDate = overrideDate;
    }
}

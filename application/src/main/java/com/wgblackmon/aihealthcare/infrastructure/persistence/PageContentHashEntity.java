package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity storing the SHA-256 content hash for a monitored web page.
 *
 * <p>Used by the web page change-detection pipeline (Slice 5) to determine
 * whether a competitor page has been updated since the last harvest.  Each
 * row tracks a single URL's most-recent content hash and check timestamp.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-04-19
 * @updated 2026-04-19
 */
@Entity
@Table(name = "page_content_hashes")
public class PageContentHashEntity {

    @Id
    @Column(name = "page_url", length = 2048)
    private String pageUrl;

    @Column(name = "content_hash", length = 64, nullable = false)
    private String contentHash;

    @Column(name = "last_checked_at", nullable = false)
    private Instant lastCheckedAt;

    /** No-arg constructor required by JPA. */
    protected PageContentHashEntity() {
    }

    public PageContentHashEntity(String pageUrl, String contentHash, Instant lastCheckedAt) {
        this.pageUrl = pageUrl;
        this.contentHash = contentHash;
        this.lastCheckedAt = lastCheckedAt;
    }

    public String getPageUrl() {
        return pageUrl;
    }

    public void setPageUrl(String pageUrl) {
        this.pageUrl = pageUrl;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public Instant getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(Instant lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }
}

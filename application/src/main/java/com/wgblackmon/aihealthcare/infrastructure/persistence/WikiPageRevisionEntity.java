package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a wiki page revision in the {@code wiki_page_revisions} table.
 *
 * <p>Every time a wiki page is updated, a new revision row is created preserving
 * the previous content.  This provides a complete audit trail so history is never lost.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Entity
@Table(name = "wiki_page_revisions")
public class WikiPageRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_slug", length = 255, nullable = false)
    private String pageSlug;

    @Column(nullable = false)
    private int revision;

    @Column(name = "content_markdown", columnDefinition = "CLOB")
    private String contentMarkdown;

    @Column(name = "compiled_at", nullable = false)
    private Instant compiledAt;

    /** Required no-arg constructor for JPA. */
    public WikiPageRevisionEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPageSlug() { return pageSlug; }
    public void setPageSlug(String pageSlug) { this.pageSlug = pageSlug; }

    public int getRevision() { return revision; }
    public void setRevision(int revision) { this.revision = revision; }

    public String getContentMarkdown() { return contentMarkdown; }
    public void setContentMarkdown(String contentMarkdown) { this.contentMarkdown = contentMarkdown; }

    public Instant getCompiledAt() { return compiledAt; }
    public void setCompiledAt(Instant compiledAt) { this.compiledAt = compiledAt; }
}

package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * JPA entity representing a provenance link in the {@code wiki_source_refs} table.
 *
 * <p>Each row links a wiki page claim back to the harvested
 * {@link com.wgblackmon.aihealthcare.domain.model.NewsArticle} that supports it.
 * Provenance is first-class: every factual claim in a wiki page should trace to
 * at least one source ref.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-04
 * @updated 2026-07-04
 */
@Entity
@Table(name = "wiki_source_refs")
public class WikiSourceRefEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "page_slug", length = 255, nullable = false)
    private String pageSlug;

    @Column(name = "article_id", length = 255, nullable = false)
    private String articleId;

    @Column(name = "source_name", length = 255, nullable = false)
    private String sourceName;

    @Column(name = "harvested_on", nullable = false)
    private LocalDate harvestedOn;

    @Column(columnDefinition = "TEXT")
    private String excerpt;

    /** Required no-arg constructor for JPA. */
    public WikiSourceRefEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPageSlug() { return pageSlug; }
    public void setPageSlug(String pageSlug) { this.pageSlug = pageSlug; }

    public String getArticleId() { return articleId; }
    public void setArticleId(String articleId) { this.articleId = articleId; }

    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }

    public LocalDate getHarvestedOn() { return harvestedOn; }
    public void setHarvestedOn(LocalDate harvestedOn) { this.harvestedOn = harvestedOn; }

    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }
}

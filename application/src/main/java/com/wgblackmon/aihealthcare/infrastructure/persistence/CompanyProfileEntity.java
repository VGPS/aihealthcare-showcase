package com.wgblackmon.aihealthcare.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a row in the {@code company_profiles} table.
 *
 * <p>The slug is the natural primary key (kebab-case company name).
 * Categories and article IDs are stored as pipe-delimited strings.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-07-22
 * @updated 2026-07-22
 */
@Entity
@Table(name = "company_profiles")
public class CompanyProfileEntity {

    @Id
    private String slug;

    private String name;

    @Column(length = 2048)
    private String url;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String categoriesPipe;

    @Column(columnDefinition = "TEXT")
    private String articleIdsPipe;

    private Instant firstDiscoveredAt;

    private Instant lastUpdatedAt;

    private int articleCount;

    private String trendDirection;

    /** Required no-arg constructor for JPA. */
    public CompanyProfileEntity() {}

    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCategoriesPipe() { return categoriesPipe; }
    public void setCategoriesPipe(String categoriesPipe) { this.categoriesPipe = categoriesPipe; }

    public String getArticleIdsPipe() { return articleIdsPipe; }
    public void setArticleIdsPipe(String articleIdsPipe) { this.articleIdsPipe = articleIdsPipe; }

    public Instant getFirstDiscoveredAt() { return firstDiscoveredAt; }
    public void setFirstDiscoveredAt(Instant firstDiscoveredAt) { this.firstDiscoveredAt = firstDiscoveredAt; }

    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(Instant lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }

    public int getArticleCount() { return articleCount; }
    public void setArticleCount(int articleCount) { this.articleCount = articleCount; }

    public String getTrendDirection() { return trendDirection; }
    public void setTrendDirection(String trendDirection) { this.trendDirection = trendDirection; }
}

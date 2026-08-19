package com.wgblackmon.aihealthcare.infrastructure.marketanalysis.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for the {@code market_digest_entry} table — one row per classified news item.
 *
 * <p>Source URLs are stored as a pipe-delimited TEXT string (e.g. "https://a.com|https://b.com"),
 * following the project's convention for list-valued fields. The adapter owns encode/decode.
 *
 * <p>Impact assessments and affected companies are stored in separate entities
 * ({@link MarketDigestImpactAssessmentEntity}, {@link MarketDigestAffectedCompanyEntity})
 * keyed by {@code entry_id} — no {@code @OneToMany} is used.
 *
 * <p>The {@code embedding} column (VECTOR(1536) for pgvector dedup) is added in Slice 1.9
 * when the embedding infrastructure is wired. It is intentionally absent here to avoid
 * a JPA/pgvector type-mapping conflict at this stage.
 *
 * @author  Bill Blackmon
 * @version 1.0
 * @since   2026-08-19
 * @updated 2026-08-19
 */
@Entity
@Table(name = "market_digest_entry")
public class MarketDigestEntryEntity {

    @Id
    @Column(name = "entry_id", nullable = false, length = 36)
    private String entryId;

    @Column(name = "digest_id", nullable = false, length = 36)
    private String digestId;

    @Column(name = "headline", nullable = false, columnDefinition = "TEXT")
    private String headline;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    /** Pipe-delimited source URLs, e.g. {@code "https://a.com|https://b.com"}. */
    @Column(name = "source_urls", columnDefinition = "TEXT")
    private String sourceUrls;

    @Column(name = "category", nullable = false, length = 32)
    private String category;

    @Column(name = "fact_classification", nullable = false, length = 16)
    private String factClassification;

    @Column(name = "market_impact_rank", nullable = false)
    private int marketImpactRank;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    /** Disclosed deal amount in USD; null when not applicable or not disclosed. */
    @Column(name = "deal_size_usd")
    private Long dealSizeUsd;

    protected MarketDigestEntryEntity() {}

    public MarketDigestEntryEntity(String entryId, String digestId, String headline,
                                   String summary, String sourceUrls, String category,
                                   String factClassification, int marketImpactRank,
                                   Instant publishedAt, Long dealSizeUsd) {
        this.entryId = entryId;
        this.digestId = digestId;
        this.headline = headline;
        this.summary = summary;
        this.sourceUrls = sourceUrls;
        this.category = category;
        this.factClassification = factClassification;
        this.marketImpactRank = marketImpactRank;
        this.publishedAt = publishedAt;
        this.dealSizeUsd = dealSizeUsd;
    }

    public String getEntryId() { return entryId; }
    public String getDigestId() { return digestId; }
    public String getHeadline() { return headline; }
    public String getSummary() { return summary; }
    public String getSourceUrls() { return sourceUrls; }
    public String getCategory() { return category; }
    public String getFactClassification() { return factClassification; }
    public int getMarketImpactRank() { return marketImpactRank; }
    public Instant getPublishedAt() { return publishedAt; }
    public Long getDealSizeUsd() { return dealSizeUsd; }
}
